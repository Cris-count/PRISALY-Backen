package com.prisaly.m3;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.prisaly.m3.application.port.CaseAuthorizationPort;
import com.prisaly.m3.application.service.CasoApplicationService;
import com.prisaly.m3.domain.builder.CasoBuilder;
import com.prisaly.m3.domain.model.Caso;
import com.prisaly.m3.domain.model.CasoEstado;
import com.prisaly.m3.domain.model.OpcionRespuesta;
import com.prisaly.m3.domain.model.CriterioRubrica;
import com.prisaly.m3.domain.model.Escenario;
import com.prisaly.m3.domain.model.Pregunta;
import com.prisaly.m3.domain.model.Rubrica;
import com.prisaly.m3.domain.rule.CasoDomainException;
import com.prisaly.m3.domain.rule.CasoValidation;
import com.prisaly.m3.domain.rule.ContenidoEvaluableLock;
import java.util.List;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class CasoApplicationServiceTest {

    // Five minutes, explicitly supplied as fixture data rather than a domain default.
    private static final int ESTIMATED_TIME_SECONDS = 300;

    private CaseAuthorizationPort authorizationPort;
    private CasoApplicationService service;

    @BeforeEach
    void setUp() {
        authorizationPort = mock(CaseAuthorizationPort.class);
        when(authorizationPort.canModifyCase(anyString(), anyString())).thenReturn(true);
        when(authorizationPort.canPublishCase(anyString(), anyString())).thenReturn(true);
        service = new CasoApplicationService(authorizationPort);
    }

    @Test
    void m3T01_createValidCase_registersAsDraft() {
        Caso caso = service.createDraft("docente-1", completeCaseBuilder("caso-1"));

        assertEquals(CasoEstado.BORRADOR, caso.estado());
        assertEquals("caso-1", caso.id());
        assertEquals(ESTIMATED_TIME_SECONDS, caso.tiempoEstimadoSegundos());
        assertFalse(caso.contenidoEvaluableBloqueado());
    }

    @Test
    void m3T02_publishCaseWithoutQuestions_isRejected() {
        Caso draft = new CasoBuilder()
                .withId("caso-2")
                .withTitulo("Caso sin preguntas")
                .withTiempoEstimadoSegundos(ESTIMATED_TIME_SECONDS)
                .withDocenteId("docente-1")
                .withRubricaId("rubrica-1")
                .addCriterio("criterio-1", "Claridad", 10)
                .buildDraft();

        CasoDomainException error = assertThrows(
                CasoDomainException.class,
                () -> service.publish("docente-1", draft));

        assertEquals(CasoValidation.CODE_CASE_INCOMPLETE, error.code());
        assertEquals(CasoEstado.BORRADOR, draft.estado());
    }

    @Test
    void m3T03_questionWithTwoCorrectAnswers_isRejected() {
        CasoBuilder builder = new CasoBuilder()
                .withId("caso-3")
                .withTitulo("Caso invalido")
                .withTiempoEstimadoSegundos(ESTIMATED_TIME_SECONDS)
                .withDocenteId("docente-1");

        CasoDomainException error = assertThrows(
                CasoDomainException.class,
                () -> builder
                        .addEscenario("escenario-1", "Situacion social")
                        .addPreguntaSeleccionUnica(
                                "pregunta-1",
                                "Que respuesta aplica?",
                                List.of(
                                        new OpcionRespuesta("op-1", "A", true),
                                        new OpcionRespuesta("op-2", "B", true)))
                        .and());

        assertEquals(CasoValidation.CODE_INCONSISTENT_OPTIONS, error.code());
    }

    @Test
    void m3T04_incompleteRubric_blocksPublication() {
        Caso draft = new CasoBuilder()
                .withId("caso-4")
                .withTitulo("Caso sin rubrica completa")
                .withTiempoEstimadoSegundos(ESTIMATED_TIME_SECONDS)
                .withDocenteId("docente-1")
                .withRubricaId("rubrica-vacia")
                .addEscenario("escenario-1", "Situacion social")
                .addPreguntaSeleccionUnica(
                        "pregunta-1",
                        "Que respuesta aplica?",
                        List.of(
                                new OpcionRespuesta("op-1", "A", true),
                                new OpcionRespuesta("op-2", "B", false)))
                .and()
                .buildDraft();

        CasoDomainException error = assertThrows(
                CasoDomainException.class,
                () -> service.publish("docente-1", draft));

        assertEquals(CasoValidation.CODE_INCOMPLETE_RUBRIC, error.code());
    }

    @Test
    void m3T05_editCaseLockedBySession_rejectsEvaluableChange() {
        Caso published = service.publish("docente-1", completeDraft("caso-5"));
        Caso locked = service.lockAfterFirstSessionScheduled(published);
        Caso replacement = completeDraft("caso-5-replacement");

        CasoDomainException error = assertThrows(
                CasoDomainException.class,
                () -> service.replaceEvaluableContent("docente-1", locked, replacement));

        assertEquals(ContenidoEvaluableLock.CODE_EVALUABLE_CONTENT_LOCKED, error.code());
        assertTrue(locked.contenidoEvaluableBloqueado());
        assertEquals(published.tiempoEstimadoSegundos(), locked.tiempoEstimadoSegundos());
    }

    @Test
    void m3T06_unauthorizedTeacherCannotPublish() {
        when(authorizationPort.canPublishCase("docente-sin-permiso", "caso-6")).thenReturn(false);
        Caso draft = completeDraft("caso-6");

        CasoDomainException error = assertThrows(
                CasoDomainException.class,
                () -> service.publish("docente-sin-permiso", draft));

        assertEquals(CasoApplicationService.CODE_FORBIDDEN, error.code());
    }

    @Test
    void m3T07_publishCompleteValidCase_becomesPublished() {
        Caso published = service.publish("docente-1", completeDraft("caso-7"));

        assertEquals(CasoEstado.PUBLICADO, published.estado());
        assertEquals(ESTIMATED_TIME_SECONDS, published.tiempoEstimadoSegundos());
        assertTrue(service.isAvailableForScheduling(published));
    }

    @Test
    void lockedCaseCanBeCopiedAsIndependentDraft() {
        Caso published = service.publish("docente-1", completeDraft("caso-8"));
        Caso locked = service.lockAfterFirstSessionScheduled(published);

        Caso copy = service.createIndependentCopy("docente-1", locked, "caso-8-copia");

        assertEquals("caso-8-copia", copy.id());
        assertEquals(locked.tiempoEstimadoSegundos(), copy.tiempoEstimadoSegundos());
        assertEquals(CasoEstado.BORRADOR, copy.estado());
        assertFalse(copy.contenidoEvaluableBloqueado());
        assertTrue(locked.contenidoEvaluableBloqueado());
    }

    @Test
    void replaceEvaluableContentWithDifferentCaseId_isRejected() {
        Caso original = completeDraft("caso-original");
        Caso replacement = completeDraft("caso-distinto");

        CasoDomainException error = assertThrows(
                CasoDomainException.class,
                () -> service.replaceEvaluableContent("docente-1", original, replacement));

        assertEquals(CasoApplicationService.CODE_CASE_ID_MISMATCH, error.code());
    }

    @Test
    void replaceEvaluableContentWithDifferentTeacher_isRejected() {
        Caso original = completeDraft("caso-original");
        Caso replacement = completeCaseBuilder(original.id())
                .withDocenteId("docente-2")
                .buildDraft();

        CasoDomainException error = assertThrows(
                CasoDomainException.class,
                () -> service.replaceEvaluableContent("docente-1", original, replacement));

        assertEquals(CasoApplicationService.CODE_CASE_OWNER_MISMATCH, error.code());
    }

    @Test
    void replaceEvaluableContentWithSameIdentityAndTeacher_returnsReplacement() {
        Caso original = completeDraft("caso-original");
        Caso replacement = completeCaseBuilder(original.id())
                .withTitulo("Contenido actualizado")
                .buildDraft();

        Caso result = service.replaceEvaluableContent("docente-1", original, replacement);

        assertEquals(replacement, result);
        assertEquals(replacement.tiempoEstimadoSegundos(), result.tiempoEstimadoSegundos());
        assertEquals("Caso de psicologia social", original.titulo());
    }

    @Test
    void unauthorizedTeacherCannotReplaceEvaluableContent() {
        Caso original = completeDraft("caso-original");
        Caso replacement = completeDraft("caso-distinto");
        when(authorizationPort.canModifyCase("docente-sin-permiso", original.id()))
                .thenReturn(false);

        CasoDomainException error = assertThrows(
                CasoDomainException.class,
                () -> service.replaceEvaluableContent("docente-sin-permiso", original, replacement));

        assertEquals(CasoApplicationService.CODE_FORBIDDEN, error.code());
    }

    @Test
    void draftWithoutEstimatedTime_isRejected() {
        CasoBuilder builder = new CasoBuilder()
                .withId("caso-sin-tiempo")
                .withTitulo("Caso sin tiempo")
                .withDocenteId("docente-1");

        CasoDomainException error = assertThrows(CasoDomainException.class, builder::buildDraft);

        assertEquals(CasoBuilder.CODE_BUILDER_INCOMPLETE, error.code());
    }

    @Test
    void draftWithZeroEstimatedTime_isRejected() {
        CasoDomainException error = assertThrows(
                CasoDomainException.class,
                () -> completeCaseBuilder("caso-cero").withTiempoEstimadoSegundos(0).buildDraft());

        assertEquals(CasoValidation.CODE_INVALID_ESTIMATED_TIME, error.code());
    }

    @Test
    void draftWithNegativeEstimatedTime_isRejected() {
        CasoDomainException error = assertThrows(
                CasoDomainException.class,
                () -> completeCaseBuilder("caso-negativo").withTiempoEstimadoSegundos(-1).buildDraft());

        assertEquals(CasoValidation.CODE_INVALID_ESTIMATED_TIME, error.code());
    }

    @Test
    void directConstructionWithNonPositiveEstimatedTime_isRejected() {
        for (int seconds : new int[] {0, -1}) {
            CasoDomainException error = assertThrows(
                    CasoDomainException.class,
                    () -> new Caso("caso-directo", "Caso", "docente-1", seconds,
                            CasoEstado.BORRADOR, List.of(), null, false));

            assertEquals(CasoValidation.CODE_INVALID_ESTIMATED_TIME, error.code());
        }
    }

    @Test
    void independentCopyRejectsSourceCaseId() {
        Caso source = completeDraft("origen");

        CasoDomainException error = assertThrows(CasoDomainException.class,
                () -> service.createIndependentCopy("docente-1", source, source.id()));

        assertEquals(ContenidoEvaluableLock.CODE_COPY_CASE_ID_EQUALS_SOURCE, error.code());
    }

    @Test
    void independentCopyRebuildsAllAcademicRecordsAndPreservesContent() {
        Caso source = richCopySource();
        Caso snapshot = richCopySource();

        Caso copy = service.createIndependentCopy("docente-1", source, "destino");

        assertEquals("destino", copy.id());
        assertEquals(source.titulo(), copy.titulo());
        assertEquals(source.docenteId(), copy.docenteId());
        assertEquals(source.tiempoEstimadoSegundos(), copy.tiempoEstimadoSegundos());
        assertEquals(CasoEstado.BORRADOR, copy.estado());
        assertFalse(copy.contenidoEvaluableBloqueado());
        assertEquals(snapshot, source);
        assertEquals(source.escenarios().size(), copy.escenarios().size());
        for (int i = 0; i < source.escenarios().size(); i++) {
            Escenario original = source.escenarios().get(i);
            Escenario copied = copy.escenarios().get(i);
            assertNotSame(original, copied);
            assertEquals(original.descripcion(), copied.descripcion());
            assertEquals(original.preguntas().size(), copied.preguntas().size());
            for (int j = 0; j < original.preguntas().size(); j++) {
                Pregunta question = original.preguntas().get(j);
                Pregunta copiedQuestion = copied.preguntas().get(j);
                assertNotSame(question, copiedQuestion);
                assertEquals(question.enunciado(), copiedQuestion.enunciado());
                assertEquals(question.opciones().size(), copiedQuestion.opciones().size());
                for (int k = 0; k < question.opciones().size(); k++) {
                    OpcionRespuesta option = question.opciones().get(k);
                    OpcionRespuesta copiedOption = copiedQuestion.opciones().get(k);
                    assertNotSame(option, copiedOption);
                    assertEquals(option.texto(), copiedOption.texto());
                    assertEquals(option.esCorrecta(), copiedOption.esCorrecta());
                }
            }
        }
        assertNotSame(source.rubrica(), copy.rubrica());
        assertEquals(source.rubrica().criterios().size(), copy.rubrica().criterios().size());
        for (int i = 0; i < source.rubrica().criterios().size(); i++) {
            CriterioRubrica original = source.rubrica().criterios().get(i);
            CriterioRubrica copied = copy.rubrica().criterios().get(i);
            assertNotSame(original, copied);
            assertEquals(original.descripcion(), copied.descripcion());
            assertEquals(original.puntaje(), copied.puntaje());
        }
        List<String> ids = internalIds(copy);
        ids.forEach(id -> assertEquals(id, UUID.fromString(id).toString()));
        assertEquals(ids.size(), new HashSet<>(ids).size());
        assertFalse(ids.contains(copy.id()));
        assertTrue(Collections.disjoint(internalIds(source), ids));
    }

    @Test
    void twoIndependentCopiesHaveDifferentInternalIds() {
        Caso source = richCopySource();
        Caso first = service.createIndependentCopy("docente-1", source, "primera");
        Caso second = service.createIndependentCopy("docente-1", source, "segunda");

        assertTrue(Collections.disjoint(internalIds(first), internalIds(second)));
    }

    @Test
    void independentCopyPreservesNullRubricAndEmptyScenarios() {
        Caso source = new Caso("vacio", "Borrador", "docente-1", ESTIMATED_TIME_SECONDS,
                CasoEstado.BORRADOR, List.of(), null, false);

        Caso copy = service.createIndependentCopy("docente-1", source, "copia-vacia");

        assertTrue(copy.escenarios().isEmpty());
        assertNull(copy.rubrica());
    }

    @Test
    void independentCopyPreservesEmptyNestedCollectionsWithoutPublicationValidation() {
        Caso source = new Caso("incompleto", "Borrador", "docente-1", ESTIMATED_TIME_SECONDS,
                CasoEstado.BORRADOR,
                List.of(new Escenario("e1", "Sin preguntas", List.of()),
                        new Escenario("e2", "Sin opciones",
                                List.of(new Pregunta("p1", "Pregunta incompleta", List.of())))),
                new Rubrica("r1", List.of()), false);

        Caso copy = service.createIndependentCopy("docente-1", source, "copia-incompleta");

        assertEquals(2, copy.escenarios().size());
        assertTrue(copy.escenarios().get(0).preguntas().isEmpty());
        assertTrue(copy.escenarios().get(1).preguntas().get(0).opciones().isEmpty());
        assertTrue(copy.rubrica().criterios().isEmpty());
        assertNotSame(source.rubrica(), copy.rubrica());
    }

    @Test
    void independentCopyChecksSourceAuthorizationBeforeRootIdentity() {
        Caso source = completeDraft("origen");
        when(authorizationPort.canModifyCase("sin-permiso", source.id())).thenReturn(false);

        CasoDomainException error = assertThrows(CasoDomainException.class,
                () -> service.createIndependentCopy("sin-permiso", source, source.id()));

        assertEquals(CasoApplicationService.CODE_FORBIDDEN, error.code());
    }

    @Test
    void independentCopyRejectsUnauthorizedDestinationWithoutChangingSource() {
        Caso source = richCopySource();
        Caso snapshot = richCopySource();
        when(authorizationPort.canModifyCase("docente-1", "destino")).thenReturn(false);

        CasoDomainException error = assertThrows(CasoDomainException.class,
                () -> service.createIndependentCopy("docente-1", source, "destino"));

        assertEquals(CasoApplicationService.CODE_FORBIDDEN, error.code());
        verify(authorizationPort).canModifyCase("docente-1", source.id());
        assertEquals(snapshot, source);
    }

    @Test
    void independentCopyDoesNotAuthorizeDestinationWhenSourceIsForbidden() {
        Caso source = completeDraft("origen");
        when(authorizationPort.canModifyCase("sin-permiso", source.id())).thenReturn(false);

        CasoDomainException error = assertThrows(CasoDomainException.class,
                () -> service.createIndependentCopy("sin-permiso", source, "destino"));

        assertEquals(CasoApplicationService.CODE_FORBIDDEN, error.code());
        verify(authorizationPort, never()).canModifyCase("sin-permiso", "destino");
    }

    private Caso richCopySource() {
        List<Escenario> scenarios = new ArrayList<>();
        for (int i = 0; i < 2; i++) {
            List<Pregunta> questions = new ArrayList<>();
            for (int j = 0; j < 2; j++) {
                String prefix = i + "-" + j;
                questions.add(new Pregunta("p-" + prefix, "Enunciado " + prefix,
                        List.of(new OpcionRespuesta("o-" + prefix + "-a", "Respuesta A " + prefix, false),
                                new OpcionRespuesta("o-" + prefix + "-b", "Respuesta B " + prefix, true))));
            }
            scenarios.add(new Escenario("e-" + i, "Escenario " + i, questions));
        }
        return new Caso("origen", "Caso original", "docente-1", ESTIMATED_TIME_SECONDS,
                CasoEstado.PUBLICADO, scenarios,
                new Rubrica("rubrica-original", List.of(
                        new CriterioRubrica("c1", "Primer criterio", 10),
                        new CriterioRubrica("c2", "Segundo criterio", 20))), true);
    }

    private List<String> internalIds(Caso caso) {
        List<String> ids = new ArrayList<>();
        for (Escenario escenario : caso.escenarios()) {
            ids.add(escenario.id());
            for (Pregunta pregunta : escenario.preguntas()) {
                ids.add(pregunta.id());
                pregunta.opciones().forEach(opcion -> ids.add(opcion.id()));
            }
        }
        if (caso.rubrica() != null) {
            ids.add(caso.rubrica().id());
            caso.rubrica().criterios().forEach(criterio -> ids.add(criterio.id()));
        }
        return ids;
    }

    private Caso completeDraft(String caseId) {
        return completeCaseBuilder(caseId).buildDraft();
    }

    private CasoBuilder completeCaseBuilder(String caseId) {
        return new CasoBuilder()
                .withId(caseId)
                .withTitulo("Caso de psicologia social")
                .withTiempoEstimadoSegundos(ESTIMATED_TIME_SECONDS)
                .withDocenteId("docente-1")
                .withRubricaId("rubrica-1")
                .addCriterio("criterio-1", "Precision conceptual", 10)
                .addEscenario("escenario-1", "Conflicto en un grupo de trabajo")
                .addPreguntaSeleccionUnica(
                        "pregunta-1",
                        "Cual es la dinamica observada?",
                        List.of(
                                new OpcionRespuesta("op-1", "Conformidad", true),
                                new OpcionRespuesta("op-2", "Obediencia", false)))
                .and();
    }
}
