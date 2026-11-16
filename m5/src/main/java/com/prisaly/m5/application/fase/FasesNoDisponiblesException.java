package com.prisaly.m5.application.fase;

import com.prisaly.m5.application.ParticipacionAplicacionException;

/** DC-BE-SIM-B1-B2A-24 (Incremento B, candidato): las fases académicas no están habilitadas en este ensamblado. No se escribió nada. */
public final class FasesNoDisponiblesException extends ParticipacionAplicacionException {
    private static final long serialVersionUID = 1L;

    public FasesNoDisponiblesException() {
        super("Las fases académicas no están habilitadas en este ensamblado");
    }
}
