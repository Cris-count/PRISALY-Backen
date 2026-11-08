package com.prisaly.m1.application.service;

import com.prisaly.m1.application.port.CuentaEstudiantePort;
import com.prisaly.m1.application.port.CuentaEstudiantePort.CredencialEstudiante;
import com.prisaly.m1.application.port.CuentaEstudiantePort.SesionNueva;
import com.prisaly.m1.application.port.CuentaEstudiantePort.VinculoEstudiante;
import com.prisaly.m1.domain.rule.IdentityDomainException;
import com.prisaly.m1.domain.student.ClaveHmacEstudiante;
import com.prisaly.m1.domain.student.FirmasEstudiante;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.Base64;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Contraseña de la cuenta propia. El resultado no incluye identificadores. */
@Service
@ConditionalOnProperty(name = "prisaly.m1.student-access.enabled", havingValue = "true")
public class AutenticacionEstudianteService {

    public static final Duration VIGENCIA_SESION = Duration.ofHours(8);

    private final CuentaEstudiantePort cuentas;
    private final PasswordEncoder passwordEncoder;
    private final ClaveHmacEstudiante clave;
    private final Clock reloj;
    private final SecureRandom aleatorio = new SecureRandom();

    @Autowired
    public AutenticacionEstudianteService(
            CuentaEstudiantePort cuentas,
            PasswordEncoder passwordEncoder,
            ClaveHmacEstudiante clave) {
        this(cuentas, passwordEncoder, clave, Clock.systemUTC());
    }

    public AutenticacionEstudianteService(
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
    public SesionEmitida autenticar(String correo, String contrasena) {
        String normalizado;
        try {
            normalizado = IdentityCatalogService.normalizeEmail(correo);
        } catch (IllegalArgumentException ex) {
            throw fallo();
        }
        if (contrasena == null || contrasena.isBlank()) {
            throw fallo();
        }
        CredencialEstudiante credencial = cuentas.porCorreo(normalizado).orElseThrow(this::fallo);
        if (!credencial.cuentaActiva()
                || credencial.passwordHash() == null
                || !passwordEncoder.matches(contrasena, credencial.passwordHash())) {
            throw fallo();
        }
        VinculoEstudiante vinculo = cuentas.vinculo(credencial.userId())
                .filter(VinculoEstudiante::cuentaActiva)
                .orElseThrow(this::fallo);
        byte[] opaco = new byte[32];
        aleatorio.nextBytes(opaco);
        String token = Base64.getUrlEncoder().withoutPadding().encodeToString(opaco);
        OffsetDateTime ahora = OffsetDateTime.now(reloj);
        cuentas.abrirSesion(new SesionNueva(
                UUID.randomUUID(),
                vinculo.userId(),
                vinculo.studentId(),
                FirmasEstudiante.hmac(clave.valor(), "sesion:" + token),
                ahora,
                ahora.plus(VIGENCIA_SESION)));
        return new SesionEmitida(token, ahora.plus(VIGENCIA_SESION).toInstant());
    }

    @Transactional
    public void revocar(String token) {
        if (token == null || token.isBlank()) {
            return;
        }
        cuentas.revocarSesion(
                FirmasEstudiante.hmac(clave.valor(), "sesion:" + token),
                OffsetDateTime.now(reloj));
    }

    private IdentityDomainException fallo() {
        return new IdentityDomainException(
                PersonnelAuthenticationService.CODE_AUTH_FAILED, "Authentication failed");
    }

    public record SesionEmitida(String token, java.time.Instant expira) {
    }
}
