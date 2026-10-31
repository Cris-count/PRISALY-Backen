package com.prisaly.m3.domain.model;

import java.util.List;

/**
 * Rubric attached to a case. One rubric per case.
 */
public record Rubrica(String id, List<CriterioRubrica> criterios) {

    public Rubrica {
        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException("Rubrica.id is required");
        }
        if (criterios == null) {
            throw new IllegalArgumentException("Rubrica.criterios is required");
        }
        criterios = List.copyOf(criterios);
    }

    public boolean isComplete() {
        return !criterios.isEmpty();
    }
}
