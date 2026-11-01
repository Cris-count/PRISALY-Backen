package com.prisaly.m5.domain.respuesta;

/**
 * Contenido opaco de la respuesta del estudiante.
 *
 * <p>Provisional: según el tipo de pregunta definido por M3 podría ser una
 * opción seleccionada o un texto; esa distinción y su formato persistente
 * siguen pendientes. Se exige un valor no vacío.
 */
public record ValorRespuesta(String valor) {

    public ValorRespuesta {
        ValidacionRespuesta.requerirTexto(valor, "El valor de la respuesta");
    }
}
