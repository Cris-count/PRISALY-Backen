package com.prisaly.m3.domain.model;

/**
 * Answer option for a single-choice question. Null orden is unassigned transient
 * metadata, not a persisted position; a PostgreSQL row requires a positive order.
 */
public record OpcionRespuesta(
        String id, String texto, boolean esCorrecta, String explicacion, Integer orden) {

    public OpcionRespuesta {
        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException("OpcionRespuesta.id is required");
        }
        if (texto == null || texto.isBlank()) {
            throw new IllegalArgumentException("OpcionRespuesta.texto is required");
        }
        if (orden != null && orden <= 0) {
            throw new IllegalArgumentException("OpcionRespuesta.orden must be greater than zero");
        }
    }

    /** Transient compatibility: no explanation or physical order is manufactured. */
    public OpcionRespuesta(String id, String texto, boolean esCorrecta) {
        this(id, texto, esCorrecta, null, null);
    }
}
