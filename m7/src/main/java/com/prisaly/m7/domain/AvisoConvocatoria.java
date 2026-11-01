package com.prisaly.m7.domain;

import java.util.Objects;

/** Solicitud de registro interno, nunca comprobante de envío o lectura. */
public record AvisoConvocatoria(ConvocatoriaNotificable evento, String contenido) {
    public static final String TIPO = "Convocatoria";

    public AvisoConvocatoria {
        Objects.requireNonNull(evento, "evento");
        if (contenido == null || contenido.isBlank()) {
            throw new IllegalArgumentException("El aviso requiere contenido");
        }
    }
}
