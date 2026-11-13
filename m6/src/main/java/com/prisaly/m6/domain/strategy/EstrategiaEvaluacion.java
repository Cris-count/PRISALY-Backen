package com.prisaly.m6.domain.strategy;

/**
 * Structural Strategy contract only. No default strategy or academic algorithm.
 * The caller must supply an explicitly selected implementation after DD-03 and
 * the academic input contract are approved. Implementations receive the validated,
 * immutable {@link EntradaEvaluacion} (C2) and must return a non-null result.
 */
@FunctionalInterface
public interface EstrategiaEvaluacion {
    ResultadoEvaluacion evaluar(EntradaEvaluacion entrada);
}
