package com.prisaly.m6.domain.strategy;

import java.math.BigDecimal;
import java.util.Objects;

/**
 * Unpersisted strategy output, not a Calificacion or a publication decision.
 * Preserves the supplied decimal and opaque document without rounding/parsing.
 * Calificacion retains its separate physical storage validation.
 */
public record ResultadoEvaluacion(BigDecimal valor, String detalleEvaluacion) {
    public ResultadoEvaluacion {
        Objects.requireNonNull(valor, "valor");
        Objects.requireNonNull(detalleEvaluacion, "detalleEvaluacion");
    }
}
