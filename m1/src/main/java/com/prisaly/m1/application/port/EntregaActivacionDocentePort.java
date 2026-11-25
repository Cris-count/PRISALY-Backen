package com.prisaly.m1.application.port;

import java.time.Instant;
import java.util.UUID;

/**
 * D-05 CANDIDATE: delivers the activation token to the teacher (institutional mail channel, pending decision). The
 * token travels only through this port: it is never returned to the administrator, logged or persisted in clear.
 * No adapter exists.
 */
@FunctionalInterface
public interface EntregaActivacionDocentePort {
    /** @throws EntregaFallidaException when the channel did not accept the message (the issue must roll back) */
    void entregar(UUID idUsuario, String correo, String token, Instant expiraEn);

    /** DC-77: the channel rejected the invitation. The message never contains the token. */
    final class EntregaFallidaException extends RuntimeException {
        public EntregaFallidaException(String motivo) {
            super(motivo);
        }
    }
}
