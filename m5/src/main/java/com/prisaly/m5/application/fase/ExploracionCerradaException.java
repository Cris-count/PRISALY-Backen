package com.prisaly.m5.application.fase;

import com.prisaly.m5.application.ParticipacionAplicacionException;

/** DC-BE-SIM-B1-B2A-24 (Incremento B, candidato): la exploración del intento está cerrada. No se escribió nada. */
public final class ExploracionCerradaException extends ParticipacionAplicacionException {
    private static final long serialVersionUID = 1L;

    public ExploracionCerradaException() {
        super("La exploración del intento está cerrada");
    }
}
