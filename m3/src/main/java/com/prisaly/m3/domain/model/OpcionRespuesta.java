package com.prisaly.m3.domain.model;

/**
 * Answer option for a single-choice question.
 */
public record OpcionRespuesta(String id, String texto, boolean esCorrecta) {

    public OpcionRespuesta {
        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException("OpcionRespuesta.id is required");
        }
        if (texto == null || texto.isBlank()) {
            throw new IllegalArgumentException("OpcionRespuesta.texto is required");
        }
    }
}
