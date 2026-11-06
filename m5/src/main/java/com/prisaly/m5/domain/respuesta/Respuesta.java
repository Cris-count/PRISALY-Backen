package com.prisaly.m5.domain.respuesta;

/**
 * Respuesta inmutable del estudiante a una pregunta dentro de un intento.
 *
 * <p>Las reglas de registro y reemplazo pertenecen a
 * {@link com.prisaly.m5.domain.intento.Intento}, que es quien conoce el estado
 * del intento.
 */
public record Respuesta(ReferenciaPregunta pregunta, ValorRespuesta valor, int revision) {

    public Respuesta(ReferenciaPregunta pregunta, ValorRespuesta valor) {
        this(pregunta, valor, 1);
    }

    public Respuesta {
        if (revision < 1) {
            throw new DatoRespuestaInvalidoException("La revisión debe ser positiva");
        }
        ValidacionRespuesta.requerirValor(pregunta, "La pregunta de la respuesta");
        ValidacionRespuesta.requerirValor(valor, "El valor de la respuesta");
    }
}
