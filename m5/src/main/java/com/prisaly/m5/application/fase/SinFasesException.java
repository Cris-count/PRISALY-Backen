package com.prisaly.m5.application.fase;

import com.prisaly.m5.application.ParticipacionAplicacionException;

/** DC-BE-SIM-B1-B2A-24 (Incremento B, candidato): el intento no tiene fases (sin_fases). No se escribió nada. */
public final class SinFasesException extends ParticipacionAplicacionException {
    private static final long serialVersionUID = 1L;

    public SinFasesException() {
        super("El intento no tiene fases (SIN_FASES)");
    }
}
