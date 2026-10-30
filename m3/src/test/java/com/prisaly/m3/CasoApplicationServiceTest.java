package com.prisaly.m3;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.prisaly.m3.application.port.CaseAuthorizationPort;
import com.prisaly.m3.application.service.CasoApplicationService;
import com.prisaly.m3.domain.builder.CasoBuilder;
import com.prisaly.m3.domain.model.Caso;
import com.prisaly.m3.domain.model.CasoEstado;
import com.prisaly.m3.domain.model.OpcionRespuesta;
import com.prisaly.m3.domain.rule.CasoDomainException;
import com.prisaly.m3.domain.rule.CasoValidation;
import com.prisaly.m3.domain.rule.ContenidoEvaluableLock;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class CasoApplicationServiceTest {

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
        assertFalse(caso.contenidoEvaluableBloqueado());
    }

    @Test
    void m3T02_publishCaseWithoutQuestions_isRejected() {
        Caso draft = new CasoBuilder()
                .withId("caso-2")
                .withTitulo("Caso sin preguntas")
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
        assertTrue(service.isAvailableForScheduling(published));
    }

    @Test
    void lockedCaseCanBeCopiedAsIndependentDraft() {
        Caso published = service.publish("docente-1", completeDraft("caso-8"));
        Caso locked = service.lockAfterFirstSessionScheduled(published);

        Caso copy = service.createIndependentCopy("docente-1", locked, "caso-8-copia");

        assertEquals("caso-8-copia", copy.id());
        assertEquals(CasoEstado.BORRADOR, copy.estado());
        assertFalse(copy.contenidoEvaluableBloqueado());
        assertTrue(locked.contenidoEvaluableBloqueado());
    }

    private Caso completeDraft(String caseId) {
        return completeCaseBuilder(caseId).buildDraft();
    }

    private CasoBuilder completeCaseBuilder(String caseId) {
        return new CasoBuilder()
                .withId(caseId)
                .withTitulo("Caso de psicologia social")
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
