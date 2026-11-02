package com.prisaly.m6.application.integration;

import static org.junit.jupiter.api.Assertions.*;

import com.prisaly.m6.application.port.ConsultaContextoAcademicoPort;
import com.prisaly.m6.application.port.ConsultaIntentoEvaluacionPort;
import com.prisaly.m6.application.integration.ContextoAcademico.*;
import com.prisaly.m6.application.integration.IntentoEvaluacion.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class ContratosConsumidoresTest {
    private static final UUID CASE = UUID.randomUUID();
    private static final UUID SCENARIO = UUID.randomUUID();
    private static final UUID QUESTION = UUID.randomUUID();
    private static final UUID OPTION = UUID.randomUUID();
    private static final UUID RUBRIC = UUID.randomUUID();

    @Test
    void attemptPreservesOfficialReferencesAndBothStatesWithoutEligibilityFlag() {
        var answer = new RespuestaReferencia(QUESTION, OPTION);
        var source = new ArrayList<>(List.of(answer));
        UUID attempt = UUID.randomUUID();
        UUID participation = UUID.randomUUID();
        for (var state : EstadoIntento.values()) {
            var dto = new IntentoEvaluacion(attempt, participation, CASE, state, source);
            assertEquals(attempt, dto.idIntento());
            assertEquals(participation, dto.idParticipacion());
            assertEquals(CASE, dto.idCaso());
            assertEquals(state, dto.estado());
            assertEquals(List.of(answer), dto.respuestas());
            assertThrows(UnsupportedOperationException.class, () -> dto.respuestas().clear());
        }
        var dto = new IntentoEvaluacion(attempt, participation, CASE, EstadoIntento.FINALIZADO, source);
        source.clear();
        assertEquals(List.of(answer), dto.respuestas());
        assertEquals(Set.of("EN_CURSO", "FINALIZADO"), Set.of(
                EstadoIntento.values()[0].name(), EstadoIntento.values()[1].name()));
        assertTrue(java.util.Arrays.stream(IntentoEvaluacion.class.getRecordComponents())
                .noneMatch(c -> c.getName().equals("evaluable")));
    }

    @Test
    void mandatoryAttemptFieldsAndResponseReferencesRejectNull() {
        UUID id = UUID.randomUUID();
        assertThrows(NullPointerException.class, () -> new IntentoEvaluacion(null, id, CASE, EstadoIntento.EN_CURSO, List.of()));
        assertThrows(NullPointerException.class, () -> new IntentoEvaluacion(id, null, CASE, EstadoIntento.EN_CURSO, List.of()));
        assertThrows(NullPointerException.class, () -> new IntentoEvaluacion(id, id, null, EstadoIntento.EN_CURSO, List.of()));
        assertThrows(NullPointerException.class, () -> new IntentoEvaluacion(id, id, CASE, null, List.of()));
        assertThrows(NullPointerException.class, () -> new IntentoEvaluacion(id, id, CASE, EstadoIntento.EN_CURSO, null));
        assertThrows(NullPointerException.class, () -> new RespuestaReferencia(null, OPTION));
        assertThrows(NullPointerException.class, () -> new RespuestaReferencia(QUESTION, null));
        assertTrue(new IntentoEvaluacion(id, id, CASE, EstadoIntento.FINALIZADO, List.of()).respuestas().isEmpty());
    }

    @Test
    void contextDeepStructurePreservesMetadataNullsOrdersAndAssociations() {
        Instant date = Instant.parse("2026-01-01T00:00:00.123456Z");
        var option = new Opcion(OPTION, QUESTION, "Correct", true, null, 19);
        var options = new ArrayList<>(List.of(option));
        var question = new Pregunta(QUESTION, SCENARIO, "Question", null, "Lista", false, 7, date, null, options);
        var questions = new ArrayList<>(List.of(question));
        var scenario = new Escenario(SCENARIO, CASE, null, "Context", null, 3, false, questions);
        var scenarios = new ArrayList<>(List.of(scenario));
        BigDecimal weight = new BigDecimal("12.500");
        var association = new RubricaPregunta(UUID.randomUUID(), RUBRIC, QUESTION, weight, null);
        var associations = new HashSet<>(Set.of(association));
        var rubric = new Rubrica(RUBRIC, CASE, "Rubric", null, null, "{\"n\":1.00}", date, associations);
        var dto = context(scenarios, rubric);
        options.clear(); questions.clear(); scenarios.clear(); associations.clear();
        assertEquals(List.of(scenario), dto.escenarios());
        assertEquals(List.of(question), scenario.preguntas());
        assertEquals(List.of(option), question.opciones());
        assertEquals(19, option.orden().intValue());
        assertEquals(7, question.orden().intValue());
        assertEquals(date, question.fechaCreacion());
        assertFalse(question.visible());
        assertFalse(scenario.activo());
        assertNull(scenario.titulo());
        assertNull(question.fechaActualizacion());
        assertNull(dto.fechaCreacion());
        assertSame(weight, rubric.preguntas().iterator().next().valorPregunta());
        assertEquals(QUESTION, association.idPregunta());
        assertEquals(RUBRIC, association.idRubrica());
        assertEquals("{\"n\":1.00}", rubric.definicionEvaluacion());
        assertThrows(UnsupportedOperationException.class, () -> dto.escenarios().clear());
        assertThrows(UnsupportedOperationException.class, () -> scenario.preguntas().clear());
        assertThrows(UnsupportedOperationException.class, () -> question.opciones().clear());
        assertThrows(UnsupportedOperationException.class, () -> rubric.preguntas().clear());
    }

    @Test
    void emptyStructuresAndUnassignedProducerMetadataAreNotCompletenessFailures() {
        assertTrue(context(List.of(), null).escenarios().isEmpty());
        assertNull(context(List.of(), null).rubrica());
        var question = new Pregunta(QUESTION, SCENARIO, "", null, "Borrador", true, null, null, null, List.of());
        var scenario = new Escenario(SCENARIO, CASE, null, null, null, null, true, List.of(question));
        assertNull(scenario.orden());
        assertTrue(question.opciones().isEmpty());
        var rubric = new Rubrica(RUBRIC, CASE, "", null, null, null, null, Set.of());
        assertTrue(context(List.of(scenario), rubric).rubrica().preguntas().isEmpty());
        assertNull(new Opcion(OPTION, QUESTION, "", false, null, null).orden());
    }

    @Test
    void sqlTypesAndBoundsAreCheckedWithoutRoundingOrAcademicFormula() {
        assertThrows(IllegalArgumentException.class, () -> new RubricaPregunta(UUID.randomUUID(), RUBRIC, QUESTION, new BigDecimal("1.001"), null));
        assertThrows(IllegalArgumentException.class, () -> new RubricaPregunta(UUID.randomUUID(), RUBRIC, QUESTION, BigDecimal.ZERO, null));
        assertThrows(IllegalArgumentException.class, () -> new Rubrica(RUBRIC, CASE, "R", null, new BigDecimal("1000000"), null, null, Set.of()));
        assertThrows(IllegalArgumentException.class, () -> new Escenario(SCENARIO, CASE, null, null, null, 0, true, List.of()));
        assertThrows(IllegalArgumentException.class, () -> new Pregunta(QUESTION, SCENARIO, "Q", null, "Otro", true, 1, null, null, List.of()));
        assertThrows(NullPointerException.class, () -> new Opcion(null, QUESTION, "A", false, null, 1));
        String supplementary = new String(Character.toChars(0x1F600)).repeat(200);
        assertEquals(supplementary, new Escenario(SCENARIO, CASE, supplementary, null, null, 1, true, List.of()).titulo());
        assertThrows(IllegalArgumentException.class, () -> new Escenario(SCENARIO, CASE, supplementary + "x", null, null, 1, true, List.of()));
    }

    @Test
    void consumerPortsDistinguishAbsenceFromTechnicalFailureUsingLocalDoubles() {
        ConsultaIntentoEvaluacionPort attempts = id -> {
            java.util.Objects.requireNonNull(id);
            return Optional.empty();
        };
        ConsultaContextoAcademicoPort contexts = (id, capacidad) -> {
            java.util.Objects.requireNonNull(id);
            java.util.Objects.requireNonNull(capacidad);
            return Optional.empty();
        };
        assertTrue(attempts.findById(UUID.randomUUID()).isEmpty());
        assertTrue(contexts.findByCasoId(CASE, "fixture").isEmpty());
        assertThrows(NullPointerException.class, () -> attempts.findById(null));
        assertThrows(NullPointerException.class, () -> contexts.findByCasoId(null, "fixture"));
        assertThrows(NullPointerException.class, () -> contexts.findByCasoId(CASE, null));
        RuntimeException error = new IllegalStateException("Producer unavailable");
        ConsultaIntentoEvaluacionPort failedAttempt = id -> { throw error; };
        ConsultaContextoAcademicoPort failedContext = (id, capacidad) -> { throw error; };
        assertSame(error, assertThrows(RuntimeException.class, () -> failedAttempt.findById(CASE)));
        assertSame(error, assertThrows(RuntimeException.class, () -> failedContext.findByCasoId(CASE, "fixture")));
        assertEquals(1, ConsultaIntentoEvaluacionPort.class.getDeclaredMethods().length);
        assertEquals(1, ConsultaContextoAcademicoPort.class.getDeclaredMethods().length);
    }

    private static ContextoAcademico context(List<Escenario> scenarios, Rubrica rubric) {
        return new ContextoAcademico(CASE, UUID.randomUUID(), "Case", null, null, 300,
                "Borrador", false, null, null, null, scenarios, rubric);
    }
}
