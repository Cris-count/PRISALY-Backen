package com.prisaly.m5.application.fase;

import com.prisaly.m5.application.ParticipacionAplicacionException;

/**
 * El cuestionario del intento no ha comenzado (409 {@code CUESTIONARIO_NO_INICIADO}). No se escribió nada.
 *
 * <p>R2 (DC-BE-SIM-B1-B2A-24): no se admiten respuestas en exploración. D-SIM-2 (DC-BE-SIM-B2B-B3-32, corregido en
 * DC-BE-SIM-B2B-B3-CORRECCION-34 / QA33-01): las preguntas no se entregan (ni por {@code /contenido} ni por la
 * completitud P4) a un intento con fases que no entró al cuestionario, también si ya finalizó.
 */
public final class CuestionarioNoIniciadoException extends ParticipacionAplicacionException {
    private static final long serialVersionUID = 1L;

    public CuestionarioNoIniciadoException() {
        this("El cuestionario del intento no ha comenzado: no se admiten respuestas en exploración");
    }

    public CuestionarioNoIniciadoException(String mensaje) {
        super(mensaje);
    }
}
