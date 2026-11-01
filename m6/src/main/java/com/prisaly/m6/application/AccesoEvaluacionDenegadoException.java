package com.prisaly.m6.application;

/** Local application rejection; no HTTP mapping or institutional permission code. */
public final class AccesoEvaluacionDenegadoException extends RuntimeException {
    public AccesoEvaluacionDenegadoException() {
        super("Evaluation access could not be authorized");
    }

    public AccesoEvaluacionDenegadoException(Throwable cause) {
        super("Evaluation access could not be authorized", cause);
    }
}
