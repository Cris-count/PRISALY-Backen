package com.prisaly.m5.domain.respuesta;

/** Validaciones compartidas por los valores del paquete de respuestas. */
final class ValidacionRespuesta {

    private ValidacionRespuesta() {
    }

    static void requerirTexto(String valor, String nombreDato) {
        if (valor == null || valor.isBlank()) {
            throw new DatoRespuestaInvalidoException(nombreDato + " es obligatorio y no puede estar vacío");
        }
    }

    static void requerirValor(Object valor, String nombreDato) {
        if (valor == null) {
            throw new DatoRespuestaInvalidoException(nombreDato + " es obligatorio");
        }
    }
}
