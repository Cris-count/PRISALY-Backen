package com.prisaly.m5.domain.respuesta;

import java.util.Locale;
import java.util.regex.Pattern;

/** Validaciones compartidas por los valores del paquete de respuestas. */
final class ValidacionRespuesta {

    private static final Pattern UUID_TEXTO =
            Pattern.compile("[0-9a-fA-F]{8}(-[0-9a-fA-F]{4}){3}-[0-9a-fA-F]{12}");

    private ValidacionRespuesta() {
    }

    static void requerirTexto(String valor, String nombreDato) {
        if (valor == null || valor.isBlank()) {
            throw new DatoRespuestaInvalidoException(nombreDato + " es obligatorio y no puede estar vacío");
        }
    }

    /** La misma referencia uuid en otra caja es la misma referencia; los valores opacos no se alteran. */
    static String canonica(String valor) {
        return UUID_TEXTO.matcher(valor).matches() ? valor.toLowerCase(Locale.ROOT) : valor;
    }

    static void requerirValor(Object valor, String nombreDato) {
        if (valor == null) {
            throw new DatoRespuestaInvalidoException(nombreDato + " es obligatorio");
        }
    }
}
