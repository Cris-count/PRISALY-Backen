package com.prisaly.m6.domain.strategy;

/**
 * Structural Strategy contract only. No default strategy or academic algorithm.
 * The caller must supply an explicitly selected implementation after DD-03 and
 * the academic input contract are approved. Implementations must accept a non-null
 * request and return a non-null result; identifiers alone are not evaluation data.
 */
@FunctionalInterface
public interface EstrategiaEvaluacion {
    ResultadoEvaluacion evaluar(SolicitudEvaluacion solicitud);
}
