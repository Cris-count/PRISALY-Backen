package com.prisaly.m5.presentation.rest;

import com.fasterxml.jackson.annotation.JsonRawValue;
import com.prisaly.m5.application.fase.CompletitudIntento;
import com.prisaly.m5.application.fase.EntradaCuestionario;
import com.prisaly.m5.application.fase.EstadoFase;
import com.prisaly.m5.application.fase.NarrativaIntento;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * DTO de las rutas estudiantiles de fases P1–P4 (DC-BE-SIM-B2B-B3-32, B3). Solo contienen información de M5 y el guion
 * publicado fijado en el intento: sin calificación, rúbrica, aciertos ni texto de preguntas. Los instantes son los
 * persistidos y {@code ahoraServidor} es el {@code clock_timestamp()} de PostgreSQL leído en la misma instantánea.
 */
public final class FasesIntentoDto {
    private FasesIntentoDto() {}

    /** P1. {@code definicion} es el documento del guion publicado tal como lo validó y persistió M3. */
    public record Narrativa(UUID idIntento, UUID idGuion, int version, String hashDefinicion,
            @JsonRawValue String definicion) {
        static Narrativa de(NarrativaIntento n) {
            return new Narrativa(n.idIntento(), n.idGuion(), n.version(), n.hashDefinicion(), n.definicionJson());
        }
    }

    /**
     * P2. {@code fase}: {@code SIN_FASES}, {@code EXPLORACION} o {@code CUESTIONARIO}; {@code estadoIntento}:
     * {@code EnCurso} o {@code Finalizado}. Las fechas de fase son {@code null} cuando no existen.
     */
    public record Fase(UUID idIntento, String estadoIntento, String fase, Instant fechaInicioExploracion,
            Instant fechaInicioCuestionario, Instant limiteTemporal, Instant ahoraServidor) {
        static Fase de(EstadoFase e) {
            return new Fase(e.idIntento(), estado(e), e.fase().name(), e.fechaInicioExploracion().orElse(null),
                    e.fechaInicioCuestionario().orElse(null), e.limiteTemporal(), e.ahoraServidor());
        }

        private static String estado(EstadoFase e) {
            return switch (e.estadoIntento()) {
                case EN_CURSO -> "EnCurso";
                case FINALIZADO -> "Finalizado";
            };
        }
    }

    /** P3. {@code yaIniciado=true} en un reintento: misma fecha persistida, sin nueva escritura. */
    public record Cuestionario(UUID idIntento, String fase, Instant fechaInicioCuestionario, boolean yaIniciado) {
        static Cuestionario de(EntradaCuestionario e) {
            return new Cuestionario(e.idIntento(), "CUESTIONARIO", e.fechaInicioCuestionario(), e.yaIniciado());
        }
    }

    /** P4, solo informativa (D-SIM-5 sin ratificar): no aprueba, no bloquea ni califica. */
    public record Completitud(UUID idIntento, int preguntasPresentables, int respondidas, List<UUID> sinResponder) {
        static Completitud de(CompletitudIntento c) {
            return new Completitud(c.idIntento(), c.preguntasPresentables(), c.respondidas(), c.sinResponder());
        }
    }
}
