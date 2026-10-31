package com.prisaly.m3;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.prisaly.m3.domain.builder.CasoBuilder;
import com.prisaly.m3.domain.model.Caso;
import com.prisaly.m3.domain.model.CasoEstado;
import com.prisaly.m3.domain.model.Escenario;
import com.prisaly.m3.domain.model.OpcionRespuesta;
import com.prisaly.m3.domain.model.Pregunta;
import com.prisaly.m3.domain.rule.CasoDomainException;
import com.prisaly.m3.domain.rule.CasoPublication;
import com.prisaly.m3.domain.rule.CasoValidation;
import com.prisaly.m3.domain.rule.ContenidoEvaluableLock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** M3-INC-09C: explicit SQL-shaped content without JPA or new academic transitions. */
class ContenidoAcademicoMetadataTest {

    private static final Instant CREATED = Instant.parse("2026-01-02T03:04:05.123456Z");
    private static final Instant UPDATED = Instant.parse("2026-01-03T04:05:06.654321Z");

    @Test
    void sqlShapedScenarioPreservesSeparateTextsAndHasNoInventedLegacyDescription() {
        Escenario scenario = new Escenario("e", "Titulo", "Contexto", "Instrucciones", 19, false, List.of());
        assertNull(scenario.descripcion());
        assertEquals("Titulo", scenario.titulo());
        assertEquals("Contexto", scenario.contexto());
        assertEquals("Instrucciones", scenario.instrucciones());
        assertEquals(Integer.valueOf(19), scenario.orden());
        assertFalse(scenario.activo());
    }

    @Test
    void optionalFieldsRemainNullWithoutManufacturingTextOrDates() {
        OpcionRespuesta option = new OpcionRespuesta("o", "Texto", false, null, 13);
        Pregunta question = new Pregunta("p", "Enunciado", List.of(option), null,
                "Borrador", false, 11, CREATED, null);
        Escenario scenario = new Escenario("e", null, null, null, 2, false, List.of(question));
        assertNull(scenario.titulo());
        assertNull(scenario.contexto());
        assertNull(scenario.instrucciones());
        assertNull(scenario.descripcion());
        assertNull(question.textoAyuda());
        assertNull(question.fechaActualizacion());
        assertNull(option.explicacion());
        assertEquals(CREATED, question.fechaCreacion());
    }

    @Test
    void questionAndOptionPreserveAllExplicitFields() {
        Pregunta question = fullQuestion("p", 11, false, "Lista");
        assertEquals("p", question.id());
        assertEquals("Enunciado p", question.enunciado());
        assertEquals("Ayuda p", question.textoAyuda());
        assertEquals("Lista", question.estadoEdicion());
        assertFalse(question.visible());
        assertEquals(Integer.valueOf(11), question.orden());
        assertEquals(CREATED, question.fechaCreacion());
        assertEquals(UPDATED, question.fechaActualizacion());
        OpcionRespuesta option = question.opciones().getFirst();
        assertEquals("p-a", option.id());
        assertEquals("A", option.texto());
        assertFalse(option.esCorrecta());
        assertEquals("Explicacion A", option.explicacion());
        assertEquals(Integer.valueOf(4), option.orden());
    }

    @Test
    void legacyConstructorsKeepUnknownPhysicalOrdersDistinctFromPersistedData() {
        OpcionRespuesta option = new OpcionRespuesta("o", "Texto", true);
        Pregunta question = new Pregunta("p", "Enunciado", List.of(option));
        Escenario scenario = new Escenario("e", "Descripcion heredada", List.of(question));
        assertEquals("Descripcion heredada", scenario.descripcion());
        assertNull(scenario.titulo());
        assertNull(scenario.contexto());
        assertNull(scenario.instrucciones());
        assertNull(scenario.orden());
        assertNull(question.orden());
        assertNull(option.orden());
        assertNull(question.fechaCreacion());
        assertNull(question.fechaActualizacion());
        assertEquals("Borrador", question.estadoEdicion());
        assertTrue(question.visible());
        assertTrue(scenario.activo());
        assertThrows(IllegalArgumentException.class, () -> new Escenario("e", " ", List.of()));
    }

    @Test
    void suppliedOrdersMustBePositive() {
        for (int invalid : new int[] {0, -1}) {
            assertThrows(IllegalArgumentException.class,
                    () -> new Escenario("e", null, null, null, invalid, true, List.of()));
            assertThrows(IllegalArgumentException.class,
                    () -> new Pregunta("p", "Enunciado", List.of(), null, "Borrador", true, invalid, null, null));
            assertThrows(IllegalArgumentException.class,
                    () -> new OpcionRespuesta("o", "Texto", false, null, invalid));
        }
    }

    @Test
    void onlyDocumentedEditingStatesAreRepresented() {
        for (String state : List.of("Borrador", "Lista")) {
            assertEquals(state, fullQuestion("p", 3, true, state).estadoEdicion());
        }
        for (String invalid : List.of("PUBLICADO", "Archivado", "lista", "")) {
            assertThrows(IllegalArgumentException.class, () -> fullQuestion("p", 3, true, invalid));
        }
        assertThrows(IllegalArgumentException.class, () -> fullQuestion("p", 3, true, null));
    }

    @Test
    void scenarioTitleRespectsV001Length() {
        assertEquals(200, new Escenario("e", "a".repeat(200), null, null, 1, true, List.of()).titulo().length());
        assertThrows(IllegalArgumentException.class,
                () -> new Escenario("e", "a".repeat(201), null, null, 1, true, List.of()));
    }

    @Test
    void builderPreservesOrdersWithGapsAndDoesNotFilterFlagsOrChangeEditingState() {
        Caso draft = completeCase();
        assertEquals(List.of(2, 19), draft.escenarios().stream().map(Escenario::orden).toList());
        assertFalse(draft.escenarios().getFirst().activo());
        assertNull(draft.escenarios().getFirst().descripcion());
        for (Escenario scenario : draft.escenarios()) {
            assertEquals(List.of(3, 11), scenario.preguntas().stream().map(Pregunta::orden).toList());
            assertFalse(scenario.preguntas().getFirst().visible());
            assertEquals("Borrador", scenario.preguntas().getFirst().estadoEdicion());
            assertEquals("Lista", scenario.preguntas().getLast().estadoEdicion());
            for (Pregunta question : scenario.preguntas()) {
                assertEquals(List.of(4, 13), question.opciones().stream().map(OpcionRespuesta::orden).toList());
                assertEquals(CREATED, question.fechaCreacion());
                assertEquals(UPDATED, question.fechaActualizacion());
            }
        }
    }

    @Test
    void builderRetainsSuppliedListOrderWithoutRenumberingOrSorting() {
        Pregunta first = fullQuestion("first", 11, false, "Lista");
        Pregunta second = fullQuestion("second", 3, true, "Borrador");
        Caso draft = caseBuilder().addEscenario("e", null, null, null, 19, false)
                .addPreguntaSeleccionUnica(first).addPreguntaSeleccionUnica(second).and().buildDraft();
        assertEquals(List.of(11, 3), draft.escenarios().getFirst().preguntas().stream().map(Pregunta::orden).toList());
        assertSame(first, draft.escenarios().getFirst().preguntas().getFirst());
    }

    @Test
    void publicationAndLockKeepAcademicRecordsWithoutLosingMetadata() {
        Caso source = completeCase();
        Caso published = CasoPublication.publish(source);
        Caso locked = ContenidoEvaluableLock.lockAfterFirstSessionScheduled(published);
        assertEquals(CasoEstado.PUBLICADO, published.estado());
        assertEquals(source.escenarios(), published.escenarios());
        assertEquals(source.escenarios(), locked.escenarios());
        assertTrue(locked.contenidoEvaluableBloqueado());
        assertNull(locked.fechaBloqueoAcademico());
        assertFalse(source.contenidoEvaluableBloqueado());
        assertEquals(CasoEstado.BORRADOR, source.estado());
    }

    @Test
    void deepCopyOnlyChangesAcademicIdsAndPreservesEveryOtherChildValue() {
        Caso source = ContenidoEvaluableLock.lockAfterFirstSessionScheduled(CasoPublication.publish(completeCase()));
        List<Escenario> originalContent = source.escenarios();
        Caso copy = ContenidoEvaluableLock.createIndependentCopy(source, "copy");
        assertEquals(CasoEstado.BORRADOR, copy.estado());
        assertFalse(copy.contenidoEvaluableBloqueado());
        assertEquals(source.escenarios().size(), copy.escenarios().size());
        for (int i = 0; i < source.escenarios().size(); i++) {
            Escenario original = source.escenarios().get(i);
            Escenario copied = copy.escenarios().get(i);
            assertNotSame(original, copied);
            assertNewId(original.id(), copied.id());
            assertEquals(new Escenario(original.id(), original.descripcion(), copied.preguntas(),
                    original.titulo(), original.contexto(), original.instrucciones(), original.orden(), original.activo()),
                    new Escenario(original.id(), copied.descripcion(), copied.preguntas(),
                            copied.titulo(), copied.contexto(), copied.instrucciones(), copied.orden(), copied.activo()));
            for (int j = 0; j < original.preguntas().size(); j++) {
                Pregunta question = original.preguntas().get(j);
                Pregunta copiedQuestion = copied.preguntas().get(j);
                assertNotSame(question, copiedQuestion);
                assertNewId(question.id(), copiedQuestion.id());
                assertEquals(new Pregunta(question.id(), question.enunciado(), copiedQuestion.opciones(),
                        question.textoAyuda(), question.estadoEdicion(), question.visible(), question.orden(),
                        question.fechaCreacion(), question.fechaActualizacion()),
                        new Pregunta(question.id(), copiedQuestion.enunciado(), copiedQuestion.opciones(),
                                copiedQuestion.textoAyuda(), copiedQuestion.estadoEdicion(), copiedQuestion.visible(),
                                copiedQuestion.orden(), copiedQuestion.fechaCreacion(), copiedQuestion.fechaActualizacion()));
                for (int k = 0; k < question.opciones().size(); k++) {
                    OpcionRespuesta option = question.opciones().get(k);
                    OpcionRespuesta copiedOption = copiedQuestion.opciones().get(k);
                    assertNotSame(option, copiedOption);
                    assertNewId(option.id(), copiedOption.id());
                    assertEquals(new OpcionRespuesta(option.id(), copiedOption.texto(), copiedOption.esCorrecta(),
                            copiedOption.explicacion(), copiedOption.orden()), option);
                }
            }
        }
        assertEquals(originalContent, source.escenarios());
        assertTrue(source.contenidoEvaluableBloqueado());
    }

    @Test
    void deepCopyPreservesNullSqlTextsAndEmptyDraftCollections() {
        Escenario empty = new Escenario("e", null, null, null, 19, false, List.of());
        Caso base = caseBuilder().buildDraft();
        Caso source = new Caso(base.id(), base.titulo(), base.docenteId(), base.tiempoEstimadoSegundos(),
                base.estado(), List.of(empty), null, false);
        Escenario copied = ContenidoEvaluableLock.createIndependentCopy(source, "copy").escenarios().getFirst();
        assertNull(copied.descripcion());
        assertNull(copied.titulo());
        assertNull(copied.contexto());
        assertNull(copied.instrucciones());
        assertEquals(Integer.valueOf(19), copied.orden());
        assertFalse(copied.activo());
        assertTrue(copied.preguntas().isEmpty());
    }

    @Test
    void explicitQuestionBuilderStillAppliesOriginalSingleChoiceValidation() {
        Pregunta invalid = new Pregunta("p", "Enunciado", List.of(
                new OpcionRespuesta("a", "A", true, null, 4),
                new OpcionRespuesta("b", "B", true, null, 13)), null, "Lista", false, 11, CREATED, UPDATED);
        CasoDomainException error = assertThrows(CasoDomainException.class,
                () -> caseBuilder().addEscenario("e", null, null, null, 2, false)
                        .addPreguntaSeleccionUnica(invalid));
        assertEquals(CasoValidation.CODE_INCONSISTENT_OPTIONS, error.code());
    }

    @Test
    void childCollectionsRemainDefensiveAndImmutable() {
        List<OpcionRespuesta> options = new ArrayList<>(fullQuestion("p", 3, true, "Lista").opciones());
        Pregunta question = new Pregunta("p", "Enunciado", options, null, "Lista", true, 3, CREATED, null);
        options.clear();
        assertEquals(2, question.opciones().size());
        List<Pregunta> questions = new ArrayList<>(List.of(question));
        Escenario scenario = new Escenario("e", null, null, null, 2, true, questions);
        questions.clear();
        assertEquals(1, scenario.preguntas().size());
        assertThrows(UnsupportedOperationException.class, () -> question.opciones().clear());
        assertThrows(UnsupportedOperationException.class, () -> scenario.preguntas().clear());
    }

    private static Caso completeCase() {
        CasoBuilder builder = caseBuilder();
        for (int i = 1; i <= 2; i++) {
            builder.addEscenario("e-" + i, "Titulo " + i, i == 1 ? null : "Contexto",
                    i == 1 ? null : "Instrucciones", i == 1 ? 2 : 19, i == 2)
                    .addPreguntaSeleccionUnica(fullQuestion("p-" + i + "-1", 3, false, "Borrador"))
                    .addPreguntaSeleccionUnica(fullQuestion("p-" + i + "-2", 11, true, "Lista"));
        }
        return builder.buildDraft();
    }

    private static CasoBuilder caseBuilder() {
        return new CasoBuilder().withId("case").withTitulo("Caso").withDocenteId("docente")
                .withTiempoEstimadoSegundos(300).withRubricaId("rubrica")
                .addCriterio("criterio", "Descripcion", 10);
    }

    private static Pregunta fullQuestion(String id, int order, boolean visible, String state) {
        return new Pregunta(id, "Enunciado " + id, List.of(
                new OpcionRespuesta(id + "-a", "A", false, "Explicacion A", 4),
                new OpcionRespuesta(id + "-b", "B", true, null, 13)),
                "Ayuda " + id, state, visible, order, CREATED, UPDATED);
    }

    private static void assertNewId(String original, String copied) {
        assertNotEquals(original, copied);
        assertEquals(copied, UUID.fromString(copied).toString());
    }
}
