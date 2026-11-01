package com.prisaly.m3.domain.model;

/**
 * Rubric criterion with a teacher-defined score weight.
 */
public record CriterioRubrica(String id, String descripcion, int puntaje) {

    public CriterioRubrica {
        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException("CriterioRubrica.id is required");
        }
        if (descripcion == null || descripcion.isBlank()) {
            throw new IllegalArgumentException("CriterioRubrica.descripcion is required");
        }
        if (puntaje <= 0) {
            throw new IllegalArgumentException("CriterioRubrica.puntaje must be greater than zero");
        }
    }
}
