package com.prisaly.m5.application.inicio;

import com.prisaly.m5.application.ParticipacionAplicacionException;

/**
 * El primer inicio no puede completarse porque falta una decisión académica.
 * Falla de forma segura: la transacción se revierte y no queda ninguna
 * participación ni intento creados.
 */
public final class InicioNoDisponibleException extends ParticipacionAplicacionException {

    private static final long serialVersionUID = 1L;

    private final DecisionPendiente decision;

    public InicioNoDisponibleException(DecisionPendiente decision) {
        super("No se puede iniciar el intento: falta ratificar " + decision.descripcion());
        this.decision = decision;
    }

    public DecisionPendiente decision() {
        return decision;
    }
}
