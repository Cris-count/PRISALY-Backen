package com.prisaly.m5.domain.intento;

import com.prisaly.m5.domain.ParticipacionDominioException;

/** Se intentó finalizar un intento sin indicar el motivo. */
public final class MotivoFinalizacionRequeridoException extends ParticipacionDominioException {

    private static final long serialVersionUID = 1L;

    public MotivoFinalizacionRequeridoException() {
        super("Finalizar un intento requiere un motivo de finalización");
    }
}
