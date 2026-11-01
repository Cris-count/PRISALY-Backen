package com.prisaly.m3.infrastructure.persistence.jpa;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/**
 * Detached storage snapshot, not a domain aggregate. Lists retain physical orden
 * within each parent; rubric associations have deliberately no ordering contract.
 * A null rubrica means there is no Rubrica row, not an incomplete domain rubric.
 */
public record CasoJpaAggregateSnapshot(
        CasoRow caso,
        List<EscenarioRow> escenarios,
        List<PreguntaRow> preguntas,
        List<OpcionRespuestaRow> opciones,
        RubricaRow rubrica,
        Set<RubricaPreguntaRow> rubricaPreguntas
) {
    public CasoJpaAggregateSnapshot {
        Objects.requireNonNull(caso, "caso");
        escenarios = List.copyOf(escenarios);
        preguntas = List.copyOf(preguntas);
        opciones = List.copyOf(opciones);
        rubricaPreguntas = Set.copyOf(rubricaPreguntas);
    }

    public record CasoRow(
            UUID idCaso, UUID idDocente, String nombre, String historia,
            String desarrolloSituacional, int tiempoEstimadoSegundos, String estado,
            boolean activo, Instant fechaBloqueoAcademico, Instant fechaCreacion,
            Instant fechaActualizacion) {
    }

    public record EscenarioRow(
            UUID idEscenario, UUID idCaso, String titulo, String contexto,
            String instrucciones, int orden, boolean activo) {
    }

    public record PreguntaRow(
            UUID idPregunta, UUID idEscenario, String enunciado, String textoAyuda,
            String estadoEdicion, boolean visible, int orden, Instant fechaCreacion,
            Instant fechaActualizacion) {
    }

    public record OpcionRespuestaRow(
            UUID idOpcion, UUID idPregunta, String texto, boolean esCorrecta,
            String explicacion, int orden) {
    }

    public record RubricaRow(
            UUID idRubrica, UUID idCaso, String nombre, String descripcion,
            BigDecimal puntajeTotal, String definicionEvaluacion, Instant fechaCreacion) {
    }

    public record RubricaPreguntaRow(
            UUID idRubricaPregunta, UUID idRubrica, UUID idPregunta,
            BigDecimal valorPregunta, String criterio) {
    }
}
