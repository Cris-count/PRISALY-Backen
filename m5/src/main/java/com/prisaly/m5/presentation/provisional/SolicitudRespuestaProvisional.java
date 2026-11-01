package com.prisaly.m5.presentation.provisional;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

import com.prisaly.m5.domain.respuesta.ReferenciaPregunta;
import com.prisaly.m5.domain.respuesta.ValorRespuesta;

/**
 * Entrada PROVISIONAL para registrar o actualizar una respuesta.
 *
 * <p>No es un contrato aprobado: ARQ-008.3 no está en el repositorio. Sus
 * campos siguen las columnas de BD-005.2 ({@code "Respuesta"."idPregunta"} y
 * {@code "idOpcionSeleccionada"}, ambas uuid). La pregunta y la opción
 * tienen que pertenecer al caso del intento; eso lo garantiza la base. No
 * incluye ni recibe puntajes ni respuestas correctas.
 */
public record SolicitudRespuestaProvisional(
        @NotBlank @Pattern(regexp = UUID_REGEX) String idPregunta,
        @NotBlank @Pattern(regexp = UUID_REGEX) String idOpcionSeleccionada) {

    /** Formato canónico de uuid (8-4-4-4-12 hexadecimal). */
    public static final String UUID_REGEX =
            "^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$";

    public ReferenciaPregunta pregunta() {
        return new ReferenciaPregunta(idPregunta);
    }

    public ValorRespuesta valor() {
        return new ValorRespuesta(idOpcionSeleccionada);
    }
}
