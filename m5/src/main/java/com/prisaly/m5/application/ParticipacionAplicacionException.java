package com.prisaly.m5.application;

/**
 * Base de las excepciones de la capa de aplicación de M5.
 *
 * <p>Las reglas de negocio violadas se siguen informando con las excepciones
 * de dominio ({@link com.prisaly.m5.domain.ParticipacionDominioException}),
 * que los servicios propagan sin envolver. Esta jerarquía cubre solo
 * situaciones propias de la coordinación, como un intento inexistente.
 */
public abstract class ParticipacionAplicacionException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    protected ParticipacionAplicacionException(String mensaje) {
        super(mensaje);
    }
}
