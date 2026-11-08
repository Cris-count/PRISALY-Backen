package com.prisaly.m3.infrastructure.persistence.jpa;

import static org.junit.jupiter.api.Assertions.*;

import com.prisaly.m3.api.ContextoAcademicoPersistido;
import com.prisaly.m3.infrastructure.persistence.jpa.CasoJpaAggregateSnapshot.*;
import java.lang.reflect.Modifier;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class ContextoAcademicoProducerMapperTest {
    private static final UUID CASE = UUID.randomUUID();
    private static final UUID OWNER = UUID.randomUUID();
    private static final UUID SCENARIO = UUID.randomUUID();
    private static final UUID QUESTION = UUID.randomUUID();
    private static final UUID RUBRIC = UUID.randomUUID();
    private static final Instant DATE = Instant.parse("2026-01-01T00:00:00.123456Z");

    @Test
    void mapsAllPhysicalValuesWithoutFilteringOrChangingSource() {
        var source = full();
        var result = ContextoAcademicoProducerMapper.map(source);
        var r = source.caso();
        assertEquals(r.idCaso(), result.idCaso()); assertEquals(r.idDocente(), result.idDocente());
        assertEquals(r.nombre(), result.nombre()); assertEquals(r.historia(), result.historia());
        assertEquals(r.desarrolloSituacional(), result.desarrolloSituacional());
        assertEquals(r.tiempoEstimadoSegundos(), result.tiempoEstimadoSegundos());
        assertEquals(r.estado(), result.estado()); assertEquals(r.activo(), result.activo());
        assertEquals(r.fechaBloqueoAcademico(), result.fechaBloqueoAcademico());
        assertEquals(r.fechaCreacion(), result.fechaCreacion()); assertEquals(r.fechaActualizacion(), result.fechaActualizacion());
        var e = result.escenarios().getFirst();
        assertEquals(new ContextoAcademicoPersistido.Escenario(SCENARIO, CASE, null, "Context", "Instructions", 7, false,
                e.preguntas()), e);
        var p = e.preguntas().getFirst();
        assertEquals(new ContextoAcademicoPersistido.Pregunta(QUESTION, SCENARIO, "Question", null, "Lista", false,
                13, DATE, null, p.opciones()), p);
        var option = source.opciones().getFirst();
        assertEquals(new ContextoAcademicoPersistido.Opcion(option.idOpcion(), QUESTION, "Correct", true, null, 4),
                p.opciones().getFirst());
        var rubric = result.rubrica();
        assertEquals(new ContextoAcademicoPersistido.Rubrica(RUBRIC, CASE, "Rubric", null,
                source.rubrica().puntajeTotal(), source.rubrica().definicionEvaluacion(), DATE, rubric.preguntas()), rubric);
        var association = source.rubricaPreguntas().iterator().next();
        assertEquals(Set.of(new ContextoAcademicoPersistido.RubricaPregunta(association.idRubricaPregunta(), RUBRIC,
                QUESTION, association.valorPregunta(), null)), rubric.preguntas());
        assertSame(source.rubrica().puntajeTotal(), rubric.puntajeTotal());
        assertSame(association.valorPregunta(), rubric.preguntas().iterator().next().valorPregunta());
        assertEquals(source, fullWith(source), "Mapping does not mutate any source row");
    }

    @Test
    void physicalOrderGapsArePreservedAndNotTakenFromInputPosition() {
        var s = full();
        var later = new OpcionRespuestaRow(UUID.randomUUID(), QUESTION, "Other", false, "Explanation", 29);
        var result = ContextoAcademicoProducerMapper.map(new CasoJpaAggregateSnapshot(s.caso(), s.escenarios(),
                s.preguntas(), List.of(later, s.opciones().getFirst()), s.rubrica(), s.rubricaPreguntas()));
        assertEquals(List.of(4, 29), result.escenarios().getFirst().preguntas().getFirst().opciones()
                .stream().map(ContextoAcademicoPersistido.Opcion::orden).toList());
    }

    @Test
    void emptyStructuresAndOptionalRubricArePreserved() {
        var s = full();
        var empty = ContextoAcademicoProducerMapper.map(new CasoJpaAggregateSnapshot(s.caso(), List.of(),
                List.of(), List.of(), null, Set.of()));
        assertTrue(empty.escenarios().isEmpty()); assertNull(empty.rubrica());
        var rubric = new RubricaRow(RUBRIC, CASE, "Rubric", null, null, null, DATE);
        var result = ContextoAcademicoProducerMapper.map(new CasoJpaAggregateSnapshot(s.caso(), s.escenarios(),
                List.of(), List.of(), rubric, Set.of()));
        assertTrue(result.escenarios().getFirst().preguntas().isEmpty());
        assertTrue(result.rubrica().preguntas().isEmpty()); assertNull(result.rubrica().puntajeTotal());
        assertNull(result.rubrica().definicionEvaluacion());
    }

    @Test
    void duplicateIdentitiesAndOrdersAreRejectedWithoutPartialResult() {
        var s = full();
        reject(new CasoJpaAggregateSnapshot(s.caso(), List.of(s.escenarios().getFirst(), s.escenarios().getFirst()),
                s.preguntas(), s.opciones(), s.rubrica(), s.rubricaPreguntas()));
        reject(new CasoJpaAggregateSnapshot(s.caso(), s.escenarios(), List.of(s.preguntas().getFirst(), s.preguntas().getFirst()),
                s.opciones(), s.rubrica(), s.rubricaPreguntas()));
        reject(new CasoJpaAggregateSnapshot(s.caso(), s.escenarios(), s.preguntas(),
                List.of(s.opciones().getFirst(), s.opciones().getFirst()), s.rubrica(), s.rubricaPreguntas()));
        reject(new CasoJpaAggregateSnapshot(s.caso(), s.escenarios(), s.preguntas(), List.of(s.opciones().getFirst(),
                new OpcionRespuestaRow(UUID.randomUUID(), QUESTION, "Other", false, null, 4)), s.rubrica(), s.rubricaPreguntas()));
        reject(new CasoJpaAggregateSnapshot(s.caso(), s.escenarios(), s.preguntas(), s.opciones(), s.rubrica(), Set.of(
                s.rubricaPreguntas().iterator().next(), new RubricaPreguntaRow(UUID.randomUUID(), RUBRIC, QUESTION, BigDecimal.ONE, null))));
        var association = s.rubricaPreguntas().iterator().next();
        reject(new CasoJpaAggregateSnapshot(s.caso(), s.escenarios(), s.preguntas(), s.opciones(), s.rubrica(), Set.of(
                association, new RubricaPreguntaRow(association.idRubricaPregunta(), RUBRIC, QUESTION, BigDecimal.ONE, "Different"))));
        reject(new CasoJpaAggregateSnapshot(s.caso(), List.of(s.escenarios().getFirst(),
                new EscenarioRow(UUID.randomUUID(), CASE, null, null, null, 7, true)),
                s.preguntas(), s.opciones(), s.rubrica(), s.rubricaPreguntas()));
        reject(new CasoJpaAggregateSnapshot(s.caso(), s.escenarios(), List.of(s.preguntas().getFirst(),
                new PreguntaRow(UUID.randomUUID(), SCENARIO, "Other", null, "Borrador", true, 13, DATE, null)),
                s.opciones(), s.rubrica(), s.rubricaPreguntas()));
    }

    @Test
    void foreignParentsOrOrphanAssociationsAreRejected() {
        var s = full();
        reject(new CasoJpaAggregateSnapshot(s.caso(), List.of(new EscenarioRow(SCENARIO, UUID.randomUUID(), null,
                null, null, 1, true)), s.preguntas(), s.opciones(), s.rubrica(), s.rubricaPreguntas()));
        reject(new CasoJpaAggregateSnapshot(s.caso(), List.of(), s.preguntas(), s.opciones(), s.rubrica(), s.rubricaPreguntas()));
        reject(new CasoJpaAggregateSnapshot(s.caso(), s.escenarios(), List.of(), s.opciones(), null, Set.of()));
        reject(new CasoJpaAggregateSnapshot(s.caso(), s.escenarios(), s.preguntas(), s.opciones(), null, s.rubricaPreguntas()));
        reject(new CasoJpaAggregateSnapshot(s.caso(), s.escenarios(), s.preguntas(), s.opciones(), s.rubrica(), Set.of(
                new RubricaPreguntaRow(UUID.randomUUID(), RUBRIC, UUID.randomUUID(), BigDecimal.ONE, null))));
        reject(new CasoJpaAggregateSnapshot(s.caso(), s.escenarios(), s.preguntas(), s.opciones(), s.rubrica(), Set.of(
                new RubricaPreguntaRow(UUID.randomUUID(), UUID.randomUUID(), QUESTION, BigDecimal.ONE, null))));
        var r = s.rubrica();
        reject(new CasoJpaAggregateSnapshot(s.caso(), s.escenarios(), s.preguntas(), s.opciones(),
                new RubricaRow(RUBRIC, UUID.randomUUID(), r.nombre(), r.descripcion(), r.puntajeTotal(),
                        r.definicionEvaluacion(), r.fechaCreacion()), s.rubricaPreguntas()));
    }

    @Test
    void dtoCollectionsAreDefensivelyCopiedAndReadOnly() {
        var mapped = ContextoAcademicoProducerMapper.map(full());
        var mutable = new ArrayList<>(mapped.escenarios());
        var copy = new ContextoAcademicoPersistido(CASE, OWNER, "Case", null, null, 300, "Borrador", true,
                null, DATE, null, mutable, mapped.rubrica());
        mutable.clear(); assertEquals(1, copy.escenarios().size());
        assertThrows(UnsupportedOperationException.class, () -> copy.escenarios().clear());
        assertThrows(UnsupportedOperationException.class, () -> copy.escenarios().getFirst().preguntas().clear());
        assertThrows(UnsupportedOperationException.class, () -> copy.escenarios().getFirst().preguntas().getFirst().opciones().clear());
        assertThrows(UnsupportedOperationException.class, () -> copy.rubrica().preguntas().clear());
    }

    @Test
    void mapperHasNoPublicOrSpringEntryPointOrAuthorizationCredential() {
        assertFalse(Modifier.isPublic(ContextoAcademicoProducerMapper.class.getModifiers()));
        assertEquals(0, ContextoAcademicoProducerMapper.class.getAnnotations().length);
        assertTrue(java.util.Arrays.stream(ContextoAcademicoProducerMapper.class.getDeclaredMethods())
                .noneMatch(m -> Modifier.isPublic(m.getModifiers())));
    }

    @Test
    void unorderedScenarioRowsAreSortedWithoutReorderingTheSnapshot() {
        var source = multipleParents();
        var result = ContextoAcademicoProducerMapper.map(source);
        assertEquals(List.of(7, 21), result.escenarios().stream()
                .map(ContextoAcademicoPersistido.Escenario::orden).toList());
        assertEquals(List.of(source.escenarios().get(1).idEscenario(), source.escenarios().getFirst().idEscenario()),
                result.escenarios().stream().map(ContextoAcademicoPersistido.Escenario::idEscenario).toList());
        assertEquals(List.of(21, 7), source.escenarios().stream().map(EscenarioRow::orden).toList());
    }

    @Test
    void interleavedQuestionsAreSortedWithinTheirOwnScenario() {
        var source = multipleParents();
        var result = ContextoAcademicoProducerMapper.map(source);
        var first = result.escenarios().getFirst();
        assertEquals(List.of(source.preguntas().get(2).idPregunta(), QUESTION),
                first.preguntas().stream().map(ContextoAcademicoPersistido.Pregunta::idPregunta).toList());
        assertEquals(List.of(3, 13), first.preguntas().stream()
                .map(ContextoAcademicoPersistido.Pregunta::orden).toList());
        var second = result.escenarios().get(1);
        assertEquals(List.of(source.preguntas().getFirst().idPregunta()), second.preguntas().stream()
                .map(ContextoAcademicoPersistido.Pregunta::idPregunta).toList());
        assertEquals(13, second.preguntas().getFirst().orden(), "Equal question order is valid in another scenario");
        assertTrue(result.escenarios().stream().allMatch(e -> e.preguntas().stream()
                .allMatch(p -> e.idEscenario().equals(p.idEscenario()))));
    }

    @Test
    void interleavedOptionsKeepTheirQuestionAndMayReuseOrderAndCorrectnessAcrossParents() {
        var source = multipleParents();
        var result = ContextoAcademicoProducerMapper.map(source);
        var questions = result.escenarios().stream().flatMap(e -> e.preguntas().stream()).toList();
        var options = questions.stream().flatMap(p -> p.opciones().stream()).toList();
        assertEquals(4, options.size());
        assertEquals(source.opciones().stream().map(OpcionRespuestaRow::idOpcion).collect(java.util.stream.Collectors.toSet()),
                options.stream().map(ContextoAcademicoPersistido.Opcion::idOpcion).collect(java.util.stream.Collectors.toSet()));
        for (var question : questions) {
            var expected = source.opciones().stream().filter(o -> o.idPregunta().equals(question.idPregunta()))
                    .sorted(java.util.Comparator.comparingInt(OpcionRespuestaRow::orden))
                    .map(o -> new ContextoAcademicoPersistido.Opcion(o.idOpcion(), o.idPregunta(), o.texto(),
                            o.esCorrecta(), o.explicacion(), o.orden())).toList();
            assertEquals(expected, question.opciones());
            assertEquals(4, question.opciones().getFirst().orden());
            assertEquals(1L, question.opciones().stream().filter(ContextoAcademicoPersistido.Opcion::esCorrecta).count());
        }
    }

    @Test
    void twoCorrectOptionsForOneQuestionAreRejectedEvenWithDistinctIdsAndOrders() {
        var source = full();
        var invalid = new CasoJpaAggregateSnapshot(source.caso(), source.escenarios(), source.preguntas(),
                List.of(source.opciones().getFirst(),
                        new OpcionRespuestaRow(UUID.randomUUID(), QUESTION, "Also correct", true, null, 29)),
                source.rubrica(), source.rubricaPreguntas());
        var failure = assertThrows(IllegalStateException.class, () -> ContextoAcademicoProducerMapper.map(invalid));
        assertEquals("Academic snapshot integrity: Multiple correct options violate V002", failure.getMessage());
    }

    @Test
    void distinctDatesKeepTheirMeaningAndNanosecondsWithoutSwappingFields() {
        var source = full();
        var root = source.caso();
        var question = source.preguntas().getFirst();
        var rubric = source.rubrica();
        Instant blocked = Instant.parse("2026-03-03T03:04:05.987654321Z");
        Instant updated = Instant.parse("2026-02-02T02:03:04.123456789Z");
        Instant questionCreated = DATE.plusNanos(1);
        Instant questionUpdated = updated.plusNanos(1);
        Instant rubricCreated = DATE.plusNanos(2);
        var distinct = new CasoJpaAggregateSnapshot(new CasoRow(root.idCaso(), root.idDocente(), root.nombre(),
                root.historia(), root.desarrolloSituacional(), root.tiempoEstimadoSegundos(), root.estado(),
                root.activo(), blocked, DATE, updated), source.escenarios(),
                List.of(new PreguntaRow(question.idPregunta(), question.idEscenario(), question.enunciado(),
                        question.textoAyuda(), question.estadoEdicion(), question.visible(), question.orden(),
                        questionCreated, questionUpdated)), source.opciones(),
                new RubricaRow(rubric.idRubrica(), rubric.idCaso(), rubric.nombre(), rubric.descripcion(),
                        rubric.puntajeTotal(), rubric.definicionEvaluacion(), rubricCreated), source.rubricaPreguntas());
        var result = ContextoAcademicoProducerMapper.map(distinct);
        assertAll(
                () -> assertEquals(blocked, result.fechaBloqueoAcademico()),
                () -> assertEquals(DATE, result.fechaCreacion()),
                () -> assertEquals(updated, result.fechaActualizacion()),
                () -> assertEquals(questionCreated, result.escenarios().getFirst().preguntas().getFirst().fechaCreacion()),
                () -> assertEquals(questionUpdated, result.escenarios().getFirst().preguntas().getFirst().fechaActualizacion()),
                () -> assertEquals(rubricCreated, result.rubrica().fechaCreacion()));
    }

    @Test
    void lateInvalidOptionOrderAbortsTheWholeMappingAndDoesNotPoisonLaterReads() {
        var source = multipleParents();
        var originalOptions = List.copyOf(source.opciones());
        var options = new ArrayList<>(originalOptions);
        options.add(new OpcionRespuestaRow(UUID.randomUUID(), QUESTION, "Invalid last row", false, null, 0));
        var invalid = new CasoJpaAggregateSnapshot(source.caso(), source.escenarios(), source.preguntas(),
                options, source.rubrica(), source.rubricaPreguntas());
        var expected = ContextoAcademicoProducerMapper.map(source);
        var returned = new java.util.concurrent.atomic.AtomicReference<ContextoAcademicoPersistido>();
        var failure = assertThrows(IllegalStateException.class,
                () -> returned.set(ContextoAcademicoProducerMapper.map(invalid)));
        assertEquals("Academic snapshot integrity: Invalid or duplicate physical order", failure.getMessage());
        assertNull(returned.get(), "No partially mapped context reaches the caller");
        assertEquals(originalOptions, source.opciones());
        assertEquals(expected, ContextoAcademicoProducerMapper.map(source));
    }

    private static CasoJpaAggregateSnapshot multipleParents() {
        var source = full();
        UUID otherScenario = UUID.randomUUID();
        UUID earlierQuestion = UUID.randomUUID();
        UUID otherQuestion = UUID.randomUUID();
        return new CasoJpaAggregateSnapshot(source.caso(),
                List.of(new EscenarioRow(otherScenario, CASE, "Other scenario", null, null, 21, true),
                        source.escenarios().getFirst()),
                List.of(new PreguntaRow(otherQuestion, otherScenario, "Other question", null, "Borrador", true, 13, DATE, null),
                        source.preguntas().getFirst(),
                        new PreguntaRow(earlierQuestion, SCENARIO, "Earlier question", "Help", "Lista", true, 3, DATE, null)),
                List.of(new OpcionRespuestaRow(UUID.randomUUID(), earlierQuestion, "Alternative", false, "Explanation", 9),
                        new OpcionRespuestaRow(UUID.randomUUID(), otherQuestion, "Other correct", true, null, 4),
                        source.opciones().getFirst(),
                        new OpcionRespuestaRow(UUID.randomUUID(), earlierQuestion, "Earlier correct", true, "Reason", 4)),
                source.rubrica(), source.rubricaPreguntas());
    }

    private static void reject(CasoJpaAggregateSnapshot s) {
        assertThrows(IllegalStateException.class, () -> ContextoAcademicoProducerMapper.map(s));
    }

    private static CasoJpaAggregateSnapshot fullWith(CasoJpaAggregateSnapshot s) {
        return new CasoJpaAggregateSnapshot(s.caso(), s.escenarios(), s.preguntas(), s.opciones(), s.rubrica(), s.rubricaPreguntas());
    }

    static CasoJpaAggregateSnapshot full() {
        return new CasoJpaAggregateSnapshot(new CasoRow(CASE, OWNER, "Case", "History", "Development", 300,
                "Borrador", false, null, DATE, DATE), List.of(new EscenarioRow(SCENARIO, CASE, null,
                        "Context", "Instructions", 7, false)), List.of(new PreguntaRow(QUESTION, SCENARIO,
                        "Question", null, "Lista", false, 13, DATE, null)),
                List.of(new OpcionRespuestaRow(UUID.randomUUID(), QUESTION, "Correct", true, null, 4)),
                new RubricaRow(RUBRIC, CASE, "Rubric", null, new BigDecimal("25.750"), "{\"n\":1.00}", DATE),
                Set.of(new RubricaPreguntaRow(UUID.randomUUID(), RUBRIC, QUESTION, new BigDecimal("12.500"), null)));
    }
}
