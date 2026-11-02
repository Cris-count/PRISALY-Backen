package com.prisaly.m6.application.integration;

import java.util.Objects;

/** Structural result only, without capability, grade, eligibility or publication decision. */
public record ContextoEvaluacionPreparado(IntentoEvaluacion intento, ContextoAcademico contextoAcademico) {
    public ContextoEvaluacionPreparado {
        Objects.requireNonNull(intento, "intento");
        Objects.requireNonNull(contextoAcademico, "contextoAcademico");
    }
}
