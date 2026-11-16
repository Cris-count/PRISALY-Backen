package com.prisaly.m5.application.fase;

import com.prisaly.m5.application.ParticipacionAplicacionException;

/** DC-BE-SIM-B1-B2A-24 (Incremento B, candidato): el intento no tiene narrativa disponible. No se escribió nada. */
public final class NarrativaNoDisponibleException extends ParticipacionAplicacionException {
    private static final long serialVersionUID = 1L;

    public NarrativaNoDisponibleException() {
        super("El intento no tiene narrativa disponible");
    }
}
