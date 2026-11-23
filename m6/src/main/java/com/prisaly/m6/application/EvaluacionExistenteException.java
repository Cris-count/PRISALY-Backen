package com.prisaly.m6.application;

/**
 * The attempt already has its single grade (M6-R9): found before evaluating, or recorded first by a concurrent
 * evaluation. Nothing is overwritten. Kept as an {@link IllegalStateException} so existing callers are unaffected.
 */
public final class EvaluacionExistenteException extends IllegalStateException {
    private static final long serialVersionUID = 1L;
    public static final String CODIGO = "EVALUACION_EXISTENTE";

    public EvaluacionExistenteException(String mensaje) {
        super(mensaje);
    }
}
