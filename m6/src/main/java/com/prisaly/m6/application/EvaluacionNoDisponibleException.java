package com.prisaly.m6.application;

/**
 * The official evaluation cannot run because no ratified strategy is selected (PF-01, DD-03). It is not an
 * authorization failure and it is raised before any attempt, context or grade is read or written.
 */
public final class EvaluacionNoDisponibleException extends RuntimeException {
    public static final String CODIGO = "ESTRATEGIA_NO_RATIFICADA";

    public EvaluacionNoDisponibleException() {
        super("No ratified evaluation strategy is selected");
    }

    public String codigo() {
        return CODIGO;
    }
}
