package com.prisaly.m5.presentation.rest;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import com.prisaly.m5.application.ResumenIntento;
import com.prisaly.m5.domain.respuesta.Respuesta;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;

/**
 * DTO del adaptador REST preparatorio de M5. Solo exponen información de M5 (D05): sin
 * calificación, rúbrica ni respuestas correctas. Los instantes provienen de las columnas
 * persistidas; {@code ahoraServidor} es la hora del servidor HTTP al construir la respuesta.
 * Este dato informativo no modifica las marcas persistidas ni decide políticas académicas.
 */
public final class IntentosM5Dto {
    private IntentosM5Dto() {}

    public record Guardar(@NotNull UUID idOpcionSeleccionada,
            @NotNull @PositiveOrZero Integer revisionEsperada) {}

    public record RespuestaGuardada(UUID idPregunta, UUID idOpcionSeleccionada, int revision) {
        static RespuestaGuardada de(Respuesta respuesta) {
            return new RespuestaGuardada(UUID.fromString(respuesta.pregunta().valor()),
                    UUID.fromString(respuesta.valor().valor()), respuesta.revision());
        }
    }

    public record Intento(UUID idIntento, String estado, Instant inicio, Instant limiteTemporal,
            String motivoFinalizacion, List<RespuestaGuardada> respuestas, Instant ahoraServidor) {
        static Intento de(ResumenIntento resumen) {
            var tiempos = resumen.tiempos().orElseThrow(
                    () -> new IllegalStateException("El resumen REST requiere los tiempos persistidos"));
            return new Intento(UUID.fromString(resumen.referencia().valor()),
                    switch (resumen.estado()) {
                        case EN_CURSO -> "EnCurso";
                        case FINALIZADO -> "Finalizado";
                    }, tiempos.inicio(), tiempos.limiteTemporal(),
                    resumen.motivoFinalizacion().map(Enum::name).orElse(null),
                    resumen.respuestas().stream().map(RespuestaGuardada::de).toList(), Instant.now());
        }
    }

    public record Inicio(Intento intento, boolean reanudado) {}

    public record Finalizacion(UUID idIntento, String estado, Instant fin, String motivoFinalizacion) {
        static Finalizacion de(ResumenIntento resumen) {
            return new Finalizacion(UUID.fromString(resumen.referencia().valor()), "Finalizado",
                    resumen.tiempos().orElseThrow(
                            () -> new IllegalStateException("El resumen REST requiere los tiempos persistidos")).fin(), resumen.motivoFinalizacion().orElseThrow().name());
        }
    }
}
