package com.prisaly.m3.domain.model;

import java.util.List;

/**
 * Scenario that groups questions inside a case.
 */
public record Escenario(String id, String descripcion, List<Pregunta> preguntas) {

    public Escenario {
        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException("Escenario.id is required");
        }
        if (descripcion == null || descripcion.isBlank()) {
            throw new IllegalArgumentException("Escenario.descripcion is required");
        }
        if (preguntas == null) {
            throw new IllegalArgumentException("Escenario.preguntas is required");
        }
        preguntas = List.copyOf(preguntas);
    }
}
