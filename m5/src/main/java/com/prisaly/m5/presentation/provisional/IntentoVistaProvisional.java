package com.prisaly.m5.presentation.provisional;

import java.util.List;

import com.prisaly.m5.application.ResumenIntento;
import com.prisaly.m5.domain.intento.EstadoIntento;
import com.prisaly.m5.domain.respuesta.Respuesta;

/**
 * Salida PROVISIONAL con el estado de un intento para el estudiante.
 *
 * <p>No es un contrato aprobado (ARQ-008.3 ausente). Solo expone lo que el
 * estudiante necesita para continuar: estado ({@code EnCurso} o
 * {@code Finalizado}, el catálogo de V007), motivo de finalización y sus
 * propias respuestas. No incluye calificación, rúbrica, retroalimentación,
 * respuestas correctas ni datos de otros estudiantes.
 */
public record IntentoVistaProvisional(
        String idIntento,
        String estado,
        String motivoFinalizacion,
        List<RespuestaVista> respuestas) {

    /** Respuesta registrada, identificada por pregunta y opción. */
    public record RespuestaVista(String idPregunta, String idOpcionSeleccionada) {
    }

    public IntentoVistaProvisional {
        respuestas = List.copyOf(respuestas);
    }

    public static IntentoVistaProvisional desde(ResumenIntento resumen) {
        return new IntentoVistaProvisional(
                resumen.referencia().valor(),
                estado(resumen.estado()),
                resumen.motivoFinalizacion().map(Enum::name).orElse(null),
                resumen.respuestas().stream().map(IntentoVistaProvisional::vista).toList());
    }

    private static String estado(EstadoIntento estado) {
        return switch (estado) {
            case EN_CURSO -> "EnCurso";
            case FINALIZADO -> "Finalizado";
        };
    }

    private static RespuestaVista vista(Respuesta respuesta) {
        return new RespuestaVista(respuesta.pregunta().valor(), respuesta.valor().valor());
    }
}
