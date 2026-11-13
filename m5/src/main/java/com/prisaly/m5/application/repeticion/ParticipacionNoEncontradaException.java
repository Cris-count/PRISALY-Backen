package com.prisaly.m5.application.repeticion;

/** La participación indicada no existe. */
public final class ParticipacionNoEncontradaException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public ParticipacionNoEncontradaException() {
        super("La participación no existe");
    }
}
