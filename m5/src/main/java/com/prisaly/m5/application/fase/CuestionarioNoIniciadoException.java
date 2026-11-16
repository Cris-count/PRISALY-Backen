package com.prisaly.m5.application.fase;

import com.prisaly.m5.application.ParticipacionAplicacionException;

/** DC-BE-SIM-B1-B2A-24 (Incremento B, candidato): el cuestionario del intento no ha comenzado: no se admiten respuestas en exploración. No se escribió nada. */
public final class CuestionarioNoIniciadoException extends ParticipacionAplicacionException {
    private static final long serialVersionUID = 1L;

    public CuestionarioNoIniciadoException() {
        super("El cuestionario del intento no ha comenzado: no se admiten respuestas en exploración");
    }
}
