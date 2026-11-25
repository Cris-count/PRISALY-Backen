package com.prisaly.m1.application.service;

import com.prisaly.m1.api.IdentityDomainException;
import com.prisaly.m1.application.port.RegistroIntentosAutenticacionPort;
import java.time.Duration;
import java.util.Objects;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * D-05 (DC-BE-D05-AUTORIZACION-QA-77): transactional entry point of {@link ActivacionDocenteService}. Not a component:
 * {@code ActivacionDocenteConfig} creates it only with {@code prisaly.m1.activacion-docente.enabled=true}.
 * <ul>
 *   <li>Issue and revoke require an active administrator ({@link AdministracionIdentidadService#exigirAdministrador})
 *       and run in one write transaction: a delivery failure rolls the new token back.</li>
 *   <li>Activation is public: failed attempts are limited per origin (and per token hash) <b>outside</b> the
 *       transaction, with a dedicated counter (not the login one). Only {@code ACTIVATION_INVALID} counts as a
 *       failure; a password rejected by DM1-08 does not consume the token nor count.</li>
 * </ul>
 */
public class GestionActivacionDocenteService {
    public static final String LOGGER_SEGURIDAD = "prisaly.seguridad.activacion";
    private static final Logger seguridad = LoggerFactory.getLogger(LOGGER_SEGURIDAD);

    private final ActivacionDocenteService nucleo;
    private final AdministracionIdentidadService administracion;
    private final RegistroIntentosAutenticacionPort intentos;
    private final TransactionTemplate transaccion;

    public GestionActivacionDocenteService(ActivacionDocenteService nucleo, AdministracionIdentidadService administracion,
            RegistroIntentosAutenticacionPort intentos, TransactionTemplate transaccion) {
        this.nucleo = Objects.requireNonNull(nucleo, "nucleo");
        this.administracion = Objects.requireNonNull(administracion, "administracion");
        this.intentos = Objects.requireNonNull(intentos, "intentos");
        this.transaccion = Objects.requireNonNull(transaccion, "transaccion");
    }

    /** Issues (or re-issues, revoking the previous one) the activation invitation of a teacher. */
    public void emitir(UUID idUsuario) {
        UUID admin = administracion.exigirAdministrador();
        transaccion.executeWithoutResult(estado -> nucleo.emitir(idUsuario, admin));
    }

    /** Revokes the live invitation of a teacher; idempotent. */
    public int revocar(UUID idUsuario) {
        UUID admin = administracion.exigirAdministrador();
        Integer revocados = transaccion.execute(estado -> nucleo.revocar(idUsuario, admin));
        return revocados == null ? 0 : revocados;
    }

    /** Public activation: consumes the token and stores the first password. */
    public void activar(String token, String password, String origen) {
        String desde = origen == null || origen.isBlank() ? "desconocido" : origen;
        String clave = "activacion:" + (token == null ? "-" : ActivacionDocenteService.hash(token));
        RegistroIntentosAutenticacionPort.Reserva reserva = intentos.reservar(clave, desde);
        if (reserva.bloqueada()) {
            long segundos = segundos(reserva.bloqueo().orElseThrow());
            seguridad.warn("SEGURIDAD_ACTIVACION evento=LIMITE_INTENTOS origen={} reintentarEnSegundos={}", desde, segundos);
            throw new AutenticacionProtegidaService.AutenticacionLimitadaException(segundos);
        }
        try {
            transaccion.executeWithoutResult(estado -> nucleo.activar(token, password));
            intentos.confirmarExito(reserva);
        } catch (IdentityDomainException rechazo) {
            if (ActivacionDocenteService.CODE_ACTIVACION_INVALIDA.equals(rechazo.code())) {
                intentos.confirmarFallo(reserva);
                seguridad.info("SEGURIDAD_ACTIVACION evento=FALLO origen={}", desde);
            } else {
                intentos.liberar(reserva);
            }
            throw rechazo;
        } catch (RuntimeException otro) {
            intentos.liberar(reserva);
            throw otro;
        }
    }

    private static long segundos(Duration espera) {
        long s = espera.toSeconds();
        return espera.toNanosPart() > 0 || s == 0 ? s + 1 : s;
    }
}
