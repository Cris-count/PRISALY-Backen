package com.prisaly.m5.domain.respuesta;

/**
 * Referencia opaca a una pregunta del caso.
 *
 * <p>Provisional: el tipo y formato definitivos del identificador de pregunta
 * dependen del contrato de M3 (ARQ-008.3) y de BD-005.2. M5 no interpreta su
 * contenido; solo lo compara por igualdad.
 */
public record ReferenciaPregunta(String valor) {

    public ReferenciaPregunta {
        ValidacionRespuesta.requerirTexto(valor, "La referencia de la pregunta");
    }
}
