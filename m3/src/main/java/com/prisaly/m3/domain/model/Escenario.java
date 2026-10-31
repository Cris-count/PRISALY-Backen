package com.prisaly.m3.domain.model;

import java.util.List;

/**
 * Scenario that groups questions inside a case. descripcion is legacy content,
 * not an alias for any SQL text. A null orden denotes unassigned transient data;
 * SQL rows require a positive order. No constructor infers it from list position.
 */
public record Escenario(
        String id, String descripcion, List<Pregunta> preguntas,
        String titulo, String contexto, String instrucciones, Integer orden, boolean activo) {

    public Escenario {
        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException("Escenario.id is required");
        }
        // V001 varchar(200) counts Unicode characters, not UTF-16 code units.
        if (titulo != null && titulo.codePointCount(0, titulo.length()) > 200) {
            throw new IllegalArgumentException("Escenario.titulo must not exceed 200 characters");
        }
        if (orden != null && orden <= 0) {
            throw new IllegalArgumentException("Escenario.orden must be greater than zero");
        }
        if (preguntas == null) {
            throw new IllegalArgumentException("Escenario.preguntas is required");
        }
        preguntas = List.copyOf(preguntas);
    }

    /** Legacy description has no approved SQL equivalence. No physical order is invented. */
    public Escenario(String id, String descripcion, List<Pregunta> preguntas) {
        this(id, requireLegacyDescription(descripcion), preguntas, null, null, null, null, true);
    }

    /** Complete SQL-shaped content; the legacy description remains unassigned. */
    public Escenario(String id, String titulo, String contexto, String instrucciones,
            int orden, boolean activo, List<Pregunta> preguntas) {
        this(id, null, preguntas, titulo, contexto, instrucciones, orden, activo);
    }

    private static String requireLegacyDescription(String descripcion) {
        if (descripcion == null || descripcion.isBlank()) {
            throw new IllegalArgumentException("Escenario.descripcion is required for legacy construction");
        }
        return descripcion;
    }
}
