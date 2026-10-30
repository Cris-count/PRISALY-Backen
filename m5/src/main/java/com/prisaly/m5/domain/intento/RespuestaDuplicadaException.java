package com.prisaly.m5.domain.intento;

import com.prisaly.m5.domain.ParticipacionDominioException;
import com.prisaly.m5.domain.respuesta.ReferenciaPregunta;

/** Se intentó registrar una segunda respuesta para la misma pregunta. */
public final class RespuestaDuplicadaException extends ParticipacionDominioException {

    private static final long serialVersionUID = 1L;

    private final transient ReferenciaPregunta pregunta;

    public RespuestaDuplicadaException(ReferenciaPregunta pregunta) {
        super("La pregunta " + pregunta.valor() + " ya tiene una respuesta registrada en este intento");
        this.pregunta = pregunta;
    }

    public ReferenciaPregunta pregunta() {
        return pregunta;
    }
}
