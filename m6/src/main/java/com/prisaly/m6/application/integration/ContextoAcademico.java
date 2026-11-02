package com.prisaly.m6.application.integration;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/** SQL-shaped consumer snapshot. No completeness, authorization or grading decision. */
public record ContextoAcademico(UUID idCaso, UUID idDocente, String nombre, String historia,
        String desarrolloSituacional, int tiempoEstimadoSegundos, String estado, boolean activo,
        Instant fechaCreacion, Instant fechaActualizacion, Instant fechaBloqueoAcademico,
        List<Escenario> escenarios, Rubrica rubrica) {
    public ContextoAcademico {
        Objects.requireNonNull(idCaso, "idCaso");
        Objects.requireNonNull(idDocente, "idDocente");
        text(nombre, 150);
        positive(tiempoEstimadoSegundos);
        if (!Set.of("Borrador", "Publicado", "Archivado").contains(Objects.requireNonNull(estado, "estado"))) {
            throw new IllegalArgumentException("Unknown case state");
        }
        // Dates may be unassigned by current M3 domain constructors; never manufacture them.
        escenarios = List.copyOf(escenarios);
    }

    public record Escenario(UUID idEscenario, UUID idCaso, String titulo, String contexto,
            String instrucciones, Integer orden, boolean activo, List<Pregunta> preguntas) {
        public Escenario {
            Objects.requireNonNull(idEscenario, "idEscenario");
            Objects.requireNonNull(idCaso, "idCaso");
            if (titulo != null) { text(titulo, 200); }
            if (orden != null) { positive(orden); }
            preguntas = List.copyOf(preguntas);
        }
    }

    public record Pregunta(UUID idPregunta, UUID idEscenario, String enunciado, String textoAyuda,
            String estadoEdicion, boolean visible, Integer orden, Instant fechaCreacion,
            Instant fechaActualizacion, List<Opcion> opciones) {
        public Pregunta {
            Objects.requireNonNull(idPregunta, "idPregunta");
            Objects.requireNonNull(idEscenario, "idEscenario");
            Objects.requireNonNull(enunciado, "enunciado");
            if (!Set.of("Borrador", "Lista").contains(Objects.requireNonNull(estadoEdicion, "estadoEdicion"))) {
                throw new IllegalArgumentException("Unknown question editing state");
            }
            if (orden != null) { positive(orden); }
            opciones = List.copyOf(opciones);
        }
    }

    public record Opcion(UUID idOpcion, UUID idPregunta, String texto, boolean esCorrecta,
            String explicacion, Integer orden) {
        public Opcion {
            Objects.requireNonNull(idOpcion, "idOpcion");
            Objects.requireNonNull(idPregunta, "idPregunta");
            Objects.requireNonNull(texto, "texto");
            if (orden != null) { positive(orden); }
        }
    }

    public record Rubrica(UUID idRubrica, UUID idCaso, String nombre, String descripcion,
            BigDecimal puntajeTotal, String definicionEvaluacion, Instant fechaCreacion,
            Set<RubricaPregunta> preguntas) {
        public Rubrica {
            Objects.requireNonNull(idRubrica, "idRubrica");
            Objects.requireNonNull(idCaso, "idCaso");
            text(nombre, 150);
            if (puntajeTotal != null) { numeric(puntajeTotal); }
            preguntas = Set.copyOf(preguntas);
        }
    }

    /** Real association, intentionally unordered and separate from legacy CriterioRubrica. */
    public record RubricaPregunta(UUID idRubricaPregunta, UUID idRubrica, UUID idPregunta,
            BigDecimal valorPregunta, String criterio) {
        public RubricaPregunta {
            Objects.requireNonNull(idRubricaPregunta, "idRubricaPregunta");
            Objects.requireNonNull(idRubrica, "idRubrica");
            Objects.requireNonNull(idPregunta, "idPregunta");
            numeric(Objects.requireNonNull(valorPregunta, "valorPregunta"));
        }
    }

    private static void text(String value, int max) {
        Objects.requireNonNull(value, "text");
        if (value.codePointCount(0, value.length()) > max) {
            throw new IllegalArgumentException("Text exceeds SQL character capacity");
        }
    }

    private static void positive(int value) {
        if (value <= 0) { throw new IllegalArgumentException("Positive integer required by V002"); }
    }

    private static void numeric(BigDecimal value) {
        if (value.signum() <= 0 || value.compareTo(new BigDecimal("999999.99")) > 0
                || value.stripTrailingZeros().scale() > 2) {
            throw new IllegalArgumentException("Positive exact NUMERIC(8,2) required");
        }
    }
}
