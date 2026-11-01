package com.prisaly.m5.domain.intento;

import com.prisaly.m5.domain.ParticipacionDominioException;

/** Se intentó una transición de estado no permitida para un intento. */
public final class TransicionIntentoInvalidaException extends ParticipacionDominioException {

    private static final long serialVersionUID = 1L;

    private final EstadoIntento origen;
    private final EstadoIntento destino;

    public TransicionIntentoInvalidaException(EstadoIntento origen, EstadoIntento destino) {
        super("Transición de intento no permitida: " + origen + " -> " + destino);
        this.origen = origen;
        this.destino = destino;
    }

    public EstadoIntento origen() {
        return origen;
    }

    public EstadoIntento destino() {
        return destino;
    }
}
