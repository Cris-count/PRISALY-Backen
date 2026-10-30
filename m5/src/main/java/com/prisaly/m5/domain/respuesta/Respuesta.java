package com.prisaly.m5.domain.respuesta;

/**
 * Respuesta inmutable del estudiante a una pregunta dentro de un intento.
 *
 * <p>Las reglas de registro y reemplazo pertenecen a
 * {@link com.prisaly.m5.domain.intento.Intento}, que es quien conoce el estado
 * del intento.
 */
public record Respuesta(ReferenciaPregunta pregunta, ValorRespuesta valor) {

    public Respuesta {
        ValidacionRespuesta.requerirValor(pregunta, "La pregunta de la respuesta");
        ValidacionRespuesta.requerirValor(valor, "El valor de la respuesta");
    }
}
