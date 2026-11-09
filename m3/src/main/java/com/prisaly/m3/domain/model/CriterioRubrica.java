package com.prisaly.m3.domain.model;

import com.prisaly.m3.domain.rule.RubricaStructureValidation;
import java.math.BigDecimal;

/**
 * Rubric criterion with a teacher-defined score and optional weight. Stored data only:
 * how they combine into a grade belongs to the pending M6 formula.
 */
public record CriterioRubrica(String id, String descripcion, int puntaje, BigDecimal ponderacion) {

    public CriterioRubrica(String id, String descripcion, int puntaje) {
        this(id, descripcion, puntaje, null);
    }

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
        if (ponderacion != null) {
            RubricaStructureValidation.requirePositiveNumeric8_2(ponderacion);
        }
    }
}
