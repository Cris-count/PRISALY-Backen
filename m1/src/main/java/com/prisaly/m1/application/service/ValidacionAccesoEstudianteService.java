package com.prisaly.m1.application.service;

import com.prisaly.m1.api.IdentityDomainException;
import com.prisaly.m1.application.port.AccesoEstudiantePort;
import com.prisaly.m1.application.port.AccesoEstudiantePort.CredencialGuardada;
import com.prisaly.m1.application.port.AccesoEstudiantePort.SesionNueva;
import com.prisaly.m1.application.port.AccesoEstudiantePort.VinculoEstudiante;
import com.prisaly.m1.application.port.LimiteAccesoEstudiantePort;
import com.prisaly.m1.domain.student.ClavesAccesoEstudiante;
import com.prisaly.m1.domain.student.FirmasEstudiante;
import com.prisaly.m1.domain.student.PoliticaCodigoAcceso;
import com.prisaly.m1.domain.student.PoliticaSesionEstudiante;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.Base64;
import java.util.Optional;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * DD-02 alternative B, {@code POST /v1/acceso-estudiante/validar} (D-B4 condition 4). The credential row is locked
 * ({@code FOR UPDATE}) and consumed with a conditional update, so concurrent validations have exactly one winner.
 * Every failure is the same {@code STUDENT_ACCESS_DENIED}: unknown or decoy challenge, wrong, expired, consumed,
 * revoked or exhausted code, inactive account or inconsistent link.
 */
@Service
@ConditionalOnProperty(name = "prisaly.m1.student-access.enabled", havingValue = "true")
public class ValidacionAccesoEstudianteService {

    public static final String CODE_STUDENT_ACCESS_DENIED = "STUDENT_ACCESS_DENIED";

    private final AccesoEstudiantePort accesos;
    private final LimiteAccesoEstudiantePort limites;
    private final ClavesAccesoEstudiante claves;
    private final Clock reloj;
    private final SecureRandom aleatorio = new SecureRandom();

    @Autowired
    public ValidacionAccesoEstudianteService(AccesoEstudiantePort accesos, LimiteAccesoEstudiantePort limites,
            ClavesAccesoEstudiante claves) {
        this(accesos, limites, claves, Clock.systemUTC());
    }

    public ValidacionAccesoEstudianteService(AccesoEstudiantePort accesos, LimiteAccesoEstudiantePort limites,
            ClavesAccesoEstudiante claves, Clock reloj) {
        this.accesos = accesos;
        this.limites = limites;
        this.claves = claves;
        this.reloj = reloj;
    }

    // A denial must commit the failed-attempt increment and the audit row (same rule as the former activation).
    @Transactional(noRollbackFor = IdentityDomainException.class)
    public SesionEmitida validar(UUID challengeId, String codigo, String origen) {
        limites.bloqueoValidacion(origen).ifPresent(espera -> {
            throw new LimiteAccesoEstudianteException(espera);
        });
        OffsetDateTime ahora = OffsetDateTime.now(reloj);
        if (challengeId == null || !PoliticaCodigoAcceso.formatoValido(codigo)) {
            throw denegado(null, origen, ahora, "FORMATO");
        }
        Optional<CredencialGuardada> encontrada = accesos.bloquearCredencial(challengeId);
        if (encontrada.isEmpty()) {
            throw denegado(null, origen, ahora, "DESAFIO_DESCONOCIDO");
        }
        CredencialGuardada c = encontrada.get();
        if (c.consumida() || c.revocada() || !c.expira().isAfter(ahora)
                || c.intentosFallidos() >= PoliticaCodigoAcceso.INTENTOS_MAXIMOS) {
            throw denegado(c.studentId(), origen, ahora, "CREDENCIAL_CERRADA");
        }
        byte[] esperado = claves.codigo().firmarCon(c.kidCodigo(), PoliticaCodigoAcceso.mensaje(challengeId, codigo))
                .orElse(null);
        if (!FirmasEstudiante.iguales(esperado, c.codigoHmac())) {
            accesos.registrarIntentoFallido(challengeId);
            throw denegado(c.studentId(), origen, ahora, "CODIGO_INCORRECTO");
        }
        if (!accesos.consumir(challengeId, ahora)) {
            throw denegado(c.studentId(), origen, ahora, "CONSUMO_PERDIDO");
        }
        VinculoEstudiante vinculo = accesos.vinculo(c.userId()).orElse(null);
        if (vinculo == null || !vinculo.cuentaActiva() || !c.studentId().equals(vinculo.studentId())) {
            throw denegado(c.studentId(), origen, ahora, "CUENTA_NO_HABILITADA");
        }
        byte[] opaco = new byte[32];
        aleatorio.nextBytes(opaco);
        String token = Base64.getUrlEncoder().withoutPadding().encodeToString(opaco);
        OffsetDateTime expira = ahora.plus(PoliticaSesionEstudiante.VIGENCIA_MAXIMA);
        accesos.abrirSesion(new SesionNueva(UUID.randomUUID(), vinculo.userId(), vinculo.studentId(),
                claves.sesion().firmar(PoliticaCodigoAcceso.mensajeSesion(token)), claves.sesion().kidActual(),
                ahora, expira));
        accesos.auditar("VALIDACION", vinculo.studentId(), "SESION_ABIERTA", origen, ahora);
        return new SesionEmitida(token, expira.toInstant());
    }

    private IdentityDomainException denegado(UUID studentId, String origen, OffsetDateTime ahora, String motivo) {
        limites.registrarValidacionFallida(origen);
        accesos.auditar("VALIDACION", studentId, "DENEGADA_" + motivo, origen, ahora);
        return new IdentityDomainException(CODE_STUDENT_ACCESS_DENIED, "Student access denied");
    }

    /** The token exists only in memory toward the cookie; only its HMAC is stored. */
    public record SesionEmitida(String token, Instant expira) {
        @Override
        public String toString() {
            return "SesionEmitida[expira=" + expira + "]";
        }
    }
}
