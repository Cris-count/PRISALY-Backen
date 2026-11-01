package com.prisaly.m3.domain.model;

import com.prisaly.m3.domain.rule.RubricaStructureValidation;
import java.math.BigDecimal;

/** Explicit SQL association. Not an equivalence or conversion of CriterioRubrica. */
public record RubricaPregunta(String idRubricaPregunta, String idRubrica,
        String idPregunta, BigDecimal valorPregunta, String criterio) {
    public RubricaPregunta {
        RubricaStructureValidation.requireUuid(idRubricaPregunta);
        RubricaStructureValidation.requireUuid(idRubrica);
        RubricaStructureValidation.requireUuid(idPregunta);
        RubricaStructureValidation.requirePositiveNumeric8_2(valorPregunta);
    }
}
