package com.prisaly.m3.api;

/** El guion narrativo no pudo leerse de forma íntegra; el consumidor debe fallar cerrado. Sin detalles técnicos. */
public final class GuionNarrativoNoDisponibleException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public GuionNarrativoNoDisponibleException() {
        super("El guion narrativo no está disponible");
    }

    public GuionNarrativoNoDisponibleException(Throwable causa) {
        super("El guion narrativo no está disponible", causa);
    }
}
