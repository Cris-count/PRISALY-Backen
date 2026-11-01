package com.prisaly.m7.domain;

import java.util.Objects;

/**
 * Solicitud de encolado de correo. Persistencia en Pendiente no es entrega SMTP.
 */
public record EnvioCorreoConvocatoria(ConvocatoriaNotificable evento, String asunto, String contenido) {

    public static final String ESTADO_PENDIENTE = "Pendiente";

    public EnvioCorreoConvocatoria {
        Objects.requireNonNull(evento, "evento");
        if (asunto == null || asunto.isBlank()) {
            throw new IllegalArgumentException("asunto is required");
        }
        if (contenido == null || contenido.isBlank()) {
            throw new IllegalArgumentException("contenido is required");
        }
    }
}
