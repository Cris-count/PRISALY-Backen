package com.prisaly.m5.domain.intento;

import com.prisaly.m5.domain.ParticipacionDominioException;

/** Se intentó modificar un intento que ya está Finalizado. */
public final class IntentoFinalizadoException extends ParticipacionDominioException {

    private static final long serialVersionUID = 1L;

    private final String operacion;

    public IntentoFinalizadoException(String operacion) {
        super("No se puede " + operacion + ": el intento está Finalizado");
        this.operacion = operacion;
    }

    /** Operación rechazada, en lenguaje del dominio. */
    public String operacion() {
        return operacion;
    }
}
