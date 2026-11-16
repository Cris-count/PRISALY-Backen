package com.prisaly.m5.application.fase;

import com.prisaly.m5.application.ParticipacionAplicacionException;

import java.util.Objects;

/** Se llegó a una situación cuyo comportamiento depende de una decisión no ratificada. No se escribió nada. */
public final class DecisionFaseNoRatificadaException extends ParticipacionAplicacionException {
    private static final long serialVersionUID = 1L;
    private final DecisionFasePendiente decision;

    public DecisionFaseNoRatificadaException(DecisionFasePendiente decision) {
        super("Comportamiento pendiente de la decisión " + Objects.requireNonNull(decision, "decision"));
        this.decision = decision;
    }

    public DecisionFasePendiente decision() {
        return decision;
    }
}
