package com.prisaly.m3.api;

/** La autoría del caso no pudo consultarse; el consumidor debe fallar cerrado. Sin detalles técnicos. */
public final class AutoriaCasoNoDisponibleException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public AutoriaCasoNoDisponibleException() {
        super("La autoría del caso no está disponible");
    }

    public AutoriaCasoNoDisponibleException(Throwable causa) {
        super("La autoría del caso no está disponible", causa);
    }
}
