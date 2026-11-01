package com.prisaly.m5.domain.intento;

import com.prisaly.m5.domain.ParticipacionDominioException;
import com.prisaly.m5.domain.respuesta.ReferenciaPregunta;

/** Se intentó actualizar la respuesta de una pregunta que aún no fue respondida. */
public final class RespuestaNoRegistradaException extends ParticipacionDominioException {

    private static final long serialVersionUID = 1L;

    private final transient ReferenciaPregunta pregunta;

    public RespuestaNoRegistradaException(ReferenciaPregunta pregunta) {
        super("La pregunta " + pregunta.valor() + " no tiene una respuesta registrada en este intento");
        this.pregunta = pregunta;
    }

    public ReferenciaPregunta pregunta() {
        return pregunta;
    }
}
