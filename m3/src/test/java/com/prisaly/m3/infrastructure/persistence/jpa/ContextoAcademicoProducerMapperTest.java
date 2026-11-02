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
