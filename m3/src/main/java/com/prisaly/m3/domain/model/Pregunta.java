package com.prisaly.m3.domain.model;

import java.util.List;

/**
 * Single-choice question belonging to a scenario.
 */
public record Pregunta(String id, String enunciado, List<OpcionRespuesta> opciones) {

    public Pregunta {
        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException("Pregunta.id is required");
        }
        if (enunciado == null || enunciado.isBlank()) {
            throw new IllegalArgumentException("Pregunta.enunciado is required");
        }
        if (opciones == null) {
            throw new IllegalArgumentException("Pregunta.opciones is required");
        }
        opciones = List.copyOf(opciones);
    }

    public long correctOptionCount() {
        return opciones.stream().filter(OpcionRespuesta::esCorrecta).count();
    }
}
