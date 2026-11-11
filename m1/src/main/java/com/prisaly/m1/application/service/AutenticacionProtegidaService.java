package com.prisaly.m1.application.service;

import com.prisaly.m1.api.IdentityDomainException;
import com.prisaly.m1.application.port.RegistroIntentosAutenticacionPort;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.HexFormat;
import java.util.Locale;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * DM1-06 (DC-M1-04): repeated-attempt protection around personnel authentication.
 * Thresholds: 5 failures per account and 20 per origin in 15 minutes (configurable). Accounts are never locked or
 * modified. The account key is a hash of the normalized email, existing or not, so limits never reveal accounts.
 */
@Service
public class AutenticacionProtegidaService {
    public static final String LOGGER_SEGURIDAD = "prisaly.seguridad.autenticacion";
    private static final Logger seguridad = LoggerFactory.getLogger(LOGGER_SEGURIDAD);

    private final PersonnelAuthenticationService autenticacion;
    private final RegistroIntentosAutenticacionPort intentos;

    public AutenticacionProtegidaService(PersonnelAuthenticationService autenticacion,
            RegistroIntentosAutenticacionPort intentos) {
        this.autenticacion = autenticacion;
        this.intentos = intentos;
    }

    public PersonnelAuthenticationService.AuthResult autenticar(String correo, String password, String origen) {
        String cuenta = claveCuenta(correo);
        String desde = origen == null || origen.isBlank() ? "desconocido" : origen;
        RegistroIntentosAutenticacionPort.Reserva reserva = intentos.reservar(cuenta, desde);
        if (reserva.bloqueada()) {
            Duration espera = reserva.bloqueo().orElseThrow();
            // The password is not evaluated while limited, for existing and non-existing accounts alike.
            seguridad.warn("SEGURIDAD_AUTENTICACION evento=LIMITE_INTENTOS cuenta={} origen={} reintentarEnSegundos={}",
                    cuenta.substring(0, 12), desde, segundos(espera));
            throw new AutenticacionLimitadaException(segundos(espera));
        }
        try {
            PersonnelAuthenticationService.AuthResult resultado = autenticacion.authenticate(correo, password);
            intentos.confirmarExito(reserva);
            return resultado;
        } catch (IdentityDomainException rechazo) {
            if (PersonnelAuthenticationService.CODE_AUTH_FAILED.equals(rechazo.code())) {
                intentos.confirmarFallo(reserva);
                seguridad.info("SEGURIDAD_AUTENTICACION evento=FALLO cuenta={} origen={}", cuenta.substring(0, 12), desde);
            } else {
                intentos.liberar(reserva);
            }
            throw rechazo;
        } catch (RuntimeException otro) {
            intentos.liberar(reserva); // e.g. token capacity: not a credential failure
            throw otro;
        }
    }

    static long segundos(Duration espera) {
        long s = espera.toSeconds();
        return espera.toNanosPart() > 0 || s == 0 ? s + 1 : s;
    }

    /** SHA-256 of the normalized email; never the email itself (logs and memory). */
    static String claveCuenta(String correo) {
        String normalizado = correo == null ? "" : correo.trim().toLowerCase(Locale.ROOT);
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(normalizado.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException(ex);
        }
    }

    /** Raised when the account or origin exceeded its failure threshold; mapped to HTTP 429 with Retry-After. */
    public static final class AutenticacionLimitadaException extends RuntimeException {
        private final long reintentarEnSegundos;

        public AutenticacionLimitadaException(long reintentarEnSegundos) {
            super("Too many authentication attempts");
            this.reintentarEnSegundos = reintentarEnSegundos;
        }

        public long reintentarEnSegundos() {
            return reintentarEnSegundos;
        }
    }
}
