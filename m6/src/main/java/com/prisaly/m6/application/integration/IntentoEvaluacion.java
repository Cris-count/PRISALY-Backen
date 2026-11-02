package com.prisaly.m6.application.integration;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

/** References only. FINALIZADO does not establish authorization or evaluability. */
public record IntentoEvaluacion(UUID idIntento, UUID idParticipacion, UUID idCaso,
        EstadoIntento estado, List<RespuestaReferencia> respuestas) {
    public IntentoEvaluacion {
        Objects.requireNonNull(idIntento, "idIntento");
        Objects.requireNonNull(idParticipacion, "idParticipacion");
        Objects.requireNonNull(idCaso, "idCaso");
        Objects.requireNonNull(estado, "estado");
        respuestas = List.copyOf(respuestas);
    }

    /** Exact lifecycle catalogue of V007/M5; no academic evaluation states. */
    public enum EstadoIntento { EN_CURSO, FINALIZADO }

    /** M5 ValorRespuesta maps to the UUID of the selected option, not free text. */
    public record RespuestaReferencia(UUID idPregunta, UUID idOpcionSeleccionada) {
        public RespuestaReferencia {
            Objects.requireNonNull(idPregunta, "idPregunta");
            Objects.requireNonNull(idOpcionSeleccionada, "idOpcionSeleccionada");
        }
    }
}
