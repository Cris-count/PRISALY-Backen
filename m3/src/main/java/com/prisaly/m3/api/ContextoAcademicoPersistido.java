package com.prisaly.m3.api;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/** Detached structural data only. Constructing this record grants no read authorization. */
public record ContextoAcademicoPersistido(UUID idCaso, UUID idDocente, String nombre,
        String historia, String desarrolloSituacional, int tiempoEstimadoSegundos,
        String estado, boolean activo, Instant fechaBloqueoAcademico, Instant fechaCreacion,
        Instant fechaActualizacion, List<Escenario> escenarios, Rubrica rubrica) {
    public ContextoAcademicoPersistido {
        Objects.requireNonNull(idCaso, "idCaso");
        Objects.requireNonNull(idDocente, "idDocente");
        Objects.requireNonNull(nombre, "nombre");
        Objects.requireNonNull(estado, "estado");
        Objects.requireNonNull(fechaCreacion, "fechaCreacion");
        escenarios = List.copyOf(escenarios);
    }

    public record Escenario(UUID idEscenario, UUID idCaso, String titulo, String contexto,
            String instrucciones, int orden, boolean activo, List<Pregunta> preguntas) {
        public Escenario {
            Objects.requireNonNull(idEscenario, "idEscenario");
            Objects.requireNonNull(idCaso, "idCaso");
            preguntas = List.copyOf(preguntas);
        }
    }

    public record Pregunta(UUID idPregunta, UUID idEscenario, String enunciado, String textoAyuda,
            String estadoEdicion, boolean visible, int orden, Instant fechaCreacion,
            Instant fechaActualizacion, List<Opcion> opciones) {
        public Pregunta {
            Objects.requireNonNull(idPregunta, "idPregunta");
            Objects.requireNonNull(idEscenario, "idEscenario");
            Objects.requireNonNull(enunciado, "enunciado");
            Objects.requireNonNull(estadoEdicion, "estadoEdicion");
            Objects.requireNonNull(fechaCreacion, "fechaCreacion");
            opciones = List.copyOf(opciones);
        }
    }

    public record Opcion(UUID idOpcion, UUID idPregunta, String texto, boolean esCorrecta,
            String explicacion, int orden) {
        public Opcion {
            Objects.requireNonNull(idOpcion, "idOpcion");
            Objects.requireNonNull(idPregunta, "idPregunta");
            Objects.requireNonNull(texto, "texto");
        }
    }

    public record Rubrica(UUID idRubrica, UUID idCaso, String nombre, String descripcion,
            BigDecimal puntajeTotal, String definicionEvaluacion, Instant fechaCreacion,
            Set<RubricaPregunta> preguntas) {
        public Rubrica {
            Objects.requireNonNull(idRubrica, "idRubrica");
            Objects.requireNonNull(idCaso, "idCaso");
            Objects.requireNonNull(nombre, "nombre");
            Objects.requireNonNull(fechaCreacion, "fechaCreacion");
            preguntas = Set.copyOf(preguntas);
        }
    }

    public record RubricaPregunta(UUID idRubricaPregunta, UUID idRubrica, UUID idPregunta,
            BigDecimal valorPregunta, String criterio) {
        public RubricaPregunta {
            Objects.requireNonNull(idRubricaPregunta, "idRubricaPregunta");
            Objects.requireNonNull(idRubrica, "idRubrica");
            Objects.requireNonNull(idPregunta, "idPregunta");
            Objects.requireNonNull(valorPregunta, "valorPregunta");
        }
    }
}
