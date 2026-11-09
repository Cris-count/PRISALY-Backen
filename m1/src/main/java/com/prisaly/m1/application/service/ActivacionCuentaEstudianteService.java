package com.prisaly.m1.application.service;

import com.prisaly.m1.application.port.CuentaEstudiantePort;
import com.prisaly.m1.application.port.CuentaEstudiantePort.ActivacionGuardada;
import com.prisaly.m1.application.port.CuentaEstudiantePort.ActivacionNueva;
import com.prisaly.m1.application.port.CuentaEstudiantePort.VinculoEstudiante;
import com.prisaly.m1.api.IdentityDomainException;
import com.prisaly.m1.domain.student.ClaveHmacEstudiante;
import com.prisaly.m1.domain.student.FirmasEstudiante;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * El código activa una cuenta ya vinculada. No abre sesión ni autentica por sí solo.
 */
@Service
@ConditionalOnProperty(name = "prisaly.m1.student-access.enabled", havingValue = "true")
public class ActivacionCuentaEstudianteService {

    public static final String CODE_STUDENT_ACCESS_DENIED = "STUDENT_ACCESS_DENIED";
    public static final Duration VIGENCIA = Duration.ofMinutes(15);
    public static final int INTENTOS_MAXIMOS = 5;
    public static final int LONGITUD_CONTRASENA = 12;

    private final CuentaEstudiantePort cuentas;
    private final PasswordEncoder passwordEncoder;
    private final ClaveHmacEstudiante clave;
    private final Clock reloj;
    private final SecureRandom aleatorio = new SecureRandom();

    @Autowired
    public ActivacionCuentaEstudianteService(
            CuentaEstudiantePort cuentas,
            PasswordEncoder passwordEncoder,
            ClaveHmacEstudiante clave) {
        this(cuentas, passwordEncoder, clave, Clock.systemUTC());
    }

    public ActivacionCuentaEstudianteService(
            CuentaEstudiantePort cuentas,
            PasswordEncoder passwordEncoder,
            ClaveHmacEstudiante clave,
            Clock reloj) {
        this.cuentas = cuentas;
        this.passwordEncoder = passwordEncoder;
        this.clave = clave;
        this.reloj = reloj;
    }

    @Transactional
    public CodigoEmitido emitir(UUID idUsuario) {
        VinculoEstudiante vinculo = cuentas.vinculo(idUsuario)
                .filter(encontrado -> !encontrado.cuentaActiva())
                .orElseThrow(this::denegado);
        OffsetDateTime ahora = OffsetDateTime.now(reloj);
        cuentas.revocarActivacionesAbiertas(idUsuario, ahora);
        String codigo = String.format("%08d", aleatorio.nextInt(100_000_000));
        UUID challengeId = UUID.randomUUID();
        cuentas.guardarActivacion(new ActivacionNueva(
                UUID.randomUUID(),
                vinculo.userId(),
                vinculo.studentId(),
                challengeId,
                FirmasEstudiante.hmac(clave.valor(), challengeId + ":" + codigo),
                ahora,
                ahora.plus(VIGENCIA)));
        return new CodigoEmitido(challengeId, codigo);
    }

    // A denial must commit the failed-attempt increment; rolling it back would never reach
    // INTENTOS_MAXIMOS. Technical errors (e.g. IllegalStateException) still roll back.
    @Transactional(noRollbackFor = IdentityDomainException.class)
    public void activar(UUID challengeId, String codigo, String contrasena) {
        if (challengeId == null || codigo == null || !codigo.matches("\\d{8}")) {
            throw denegado();
        }
        if (contrasena == null || contrasena.length() < LONGITUD_CONTRASENA || contrasena.equals(codigo)) {
            throw denegado();
        }
        Optional<ActivacionGuardada> encontrada = cuentas.buscarActivacion(challengeId);
        if (encontrada.isEmpty()) {
            throw denegado();
        }
        ActivacionGuardada activacion = encontrada.get();
        OffsetDateTime ahora = OffsetDateTime.now(reloj);
        boolean vigente = !activacion.consumida()
                && !activacion.revocada()
                && activacion.intentosFallidos() < INTENTOS_MAXIMOS
                && activacion.expira().isAfter(ahora);
        byte[] firma = FirmasEstudiante.hmac(clave.valor(), challengeId + ":" + codigo);
        if (!vigente || !FirmasEstudiante.iguales(activacion.codigoHmac(), firma)) {
            if (vigente) {
                cuentas.registrarIntentoFallido(challengeId);
            }
            throw denegado();
        }
        VinculoEstudiante vinculo = cuentas.vinculo(activacion.userId()).orElseThrow(this::denegado);
        if (vinculo.cuentaActiva()
                || !vinculo.studentId().equals(activacion.studentId())
                || !vinculo.userId().equals(activacion.userId())) {
            throw denegado();
        }
        boolean activada = cuentas.consumirYActivar(
                challengeId, passwordEncoder.encode(contrasena), ahora);
        if (!activada) {
            throw denegado();
        }
    }

    private IdentityDomainException denegado() {
        return new IdentityDomainException(CODE_STUDENT_ACCESS_DENIED, "Student access denied");
    }

    public record CodigoEmitido(UUID challengeId, String codigo) {
    }
}
