package com.prisaly.m5.domain.respuesta;

import com.prisaly.m5.domain.ParticipacionDominioException;

/** Un dato necesario para construir una respuesta es nulo o está vacío. */
public final class DatoRespuestaInvalidoException extends ParticipacionDominioException {

    private static final long serialVersionUID = 1L;

    public DatoRespuestaInvalidoException(String mensaje) {
        super(mensaje);
    }
}
