package com.prisaly.m1.application.service;

import com.prisaly.m1.application.port.AccesoEstudiantePort;
import com.prisaly.m1.application.port.AccesoEstudiantePort.CredencialNueva;
import com.prisaly.m1.application.port.AccesoEstudiantePort.VinculoEstudiante;
import com.prisaly.m1.application.port.CodigoAccesoDeliveryCommand;
import com.prisaly.m1.application.port.LimiteAccesoEstudiantePort;
import com.prisaly.m1.application.port.StudentAccessDeliveryPort;
import com.prisaly.m1.domain.student.ClavesAccesoEstudiante;
import com.prisaly.m1.domain.student.PoliticaCodigoAcceso;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * DD-02 alternative B, {@code POST /v1/acceso-estudiante/solicitar} (D-B4 condition 1-3). The answer is ALWAYS an
 * opaque random {@code challengeId}: identical shape for registered and unregistered emails, and also when queuing
 * fails (D-B2.5: the credential is rolled back and nothing is sent). Limits are applied to the email string before
 * looking the account up, so a 429 never reveals whether the account exists.
 */
@Service
@ConditionalOnProperty(name = "prisaly.m1.student-access.enabled", havingValue = "true")
public class SolicitudAccesoEstudianteService {

    private static final Logger log = LoggerFactory.getLogger("prisaly.auditoria.seguridad");

    private final AccesoEstudiantePort accesos;
    private final LimiteAccesoEstudiantePort limites;
    private final StudentAccessDeliveryPort entrega;
    private final ClavesAccesoEstudiante claves;
    private final TransactionTemplate transaccion;
    private final Clock reloj;

    @Autowired
    public SolicitudAccesoEstudianteService(AccesoEstudiantePort accesos, LimiteAccesoEstudiantePort limites,
            StudentAccessDeliveryPort entrega, ClavesAccesoEstudiante claves, PlatformTransactionManager transacciones) {
        this(accesos, limites, entrega, claves, new TransactionTemplate(transacciones), Clock.systemUTC());
    }

    public SolicitudAccesoEstudianteService(AccesoEstudiantePort accesos, LimiteAccesoEstudiantePort limites,
            StudentAccessDeliveryPort entrega, ClavesAccesoEstudiante claves, TransactionTemplate transaccion,
            Clock reloj) {
        this.accesos = accesos;
        this.limites = limites;
        this.entrega = entrega;
        this.claves = claves;
        this.transaccion = transaccion;
        this.reloj = reloj;
    }

    /**
     * @param correo email as typed by the client (already shape-validated by the controller)
     * @param origen TCP peer address
     * @return opaque challenge id (random also for unknown emails)
     * @throws LimiteAccesoEstudianteException when a D-B2.1/D-B2.2 limit applies
     */
    public UUID solicitar(String correo, String origen) {
        String normalizado = IdentityCatalogService.normalizeEmail(correo);
        limites.registrarSolicitud(normalizado, origen).ifPresent(espera -> {
            throw new LimiteAccesoEstudianteException(espera);
        });
        UUID challengeId = UUID.randomUUID();
        // The code and its HMAC are always computed, whether or not an account exists (similar work per request).
        String codigo = PoliticaCodigoAcceso.generar();
        byte[] codigoHmac = claves.codigo().firmar(PoliticaCodigoAcceso.mensaje(challengeId, codigo));
        try {
            transaccion.executeWithoutResult(estado -> emitir(normalizado, origen, challengeId, codigo, codigoHmac));
        } catch (RuntimeException fallo) {
            // DD02-12a: queuing (or any write) failed, the whole transaction rolled back: no usable credential.
            // Only the exception type is logged; never the email, code or HMAC.
            log.warn("SEGURIDAD_ESTUDIANTE evento=SOLICITUD resultado=ENCOLADO_FALLIDO tipo={}",
                    fallo.getClass().getSimpleName());
        }
        return challengeId;
    }

    private void emitir(String normalizado, String origen, UUID challengeId, String codigo, byte[] codigoHmac) {
        OffsetDateTime ahora = OffsetDateTime.now(reloj);
        Optional<VinculoEstudiante> vinculo = accesos.vinculoPorCorreo(normalizado)
                .filter(v -> v.cuentaActiva() && v.studentId() != null);
        if (vinculo.isEmpty()) {
            accesos.auditar("SOLICITUD", null, "SIN_CUENTA_HABILITADA", origen, ahora);
            return;
        }
        VinculoEstudiante v = vinculo.get();
        OffsetDateTime expira = ahora.plus(PoliticaCodigoAcceso.VIGENCIA);
        accesos.revocarCredencialesAbiertas(v.userId(), ahora);
        accesos.guardarCredencial(new CredencialNueva(UUID.randomUUID(), v.userId(), v.studentId(), challengeId,
                codigoHmac, claves.codigo().kidActual(), ahora, expira));
        // Same transaction (Propagation.MANDATORY in the M7 Outbox adapter): queue failure rolls back the credential.
        entrega.entregarCodigo(new CodigoAccesoDeliveryCommand(challengeId.toString(), v.correo(), codigo,
                expira.toInstant(), "codigo-acceso:" + challengeId));
        accesos.auditar("SOLICITUD", v.studentId(), "CODIGO_EMITIDO", origen, ahora);
    }
}
