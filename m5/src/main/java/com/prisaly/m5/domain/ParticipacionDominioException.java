package com.prisaly.m5.domain;

/**
 * Base de las excepciones de dominio de M5.
 *
 * <p>No depende de HTTP: la traducción a respuestas de la API corresponde a la
 * capa de presentación.
 */
public abstract class ParticipacionDominioException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    protected ParticipacionDominioException(String mensaje) {
        super(mensaje);
    }
}
