package com.prisaly.m7.domain;

import java.util.Objects;

/** Outcome of one SMTP attempt. Acceptance != mailbox delivery. */
public sealed interface ResultadoEnvioSmtp {

    record Aceptado(String messageId) implements ResultadoEnvioSmtp {
        public Aceptado {
            Objects.requireNonNull(messageId, "messageId");
        }
    }

    record Rechazado(ClaseErrorCorreo clase, String codigo, String detalle) implements ResultadoEnvioSmtp {
        public Rechazado {
            Objects.requireNonNull(clase, "clase");
            Objects.requireNonNull(codigo, "codigo");
            Objects.requireNonNull(detalle, "detalle");
        }
    }
}
