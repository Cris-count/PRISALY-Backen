package com.prisaly.m6.application.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.prisaly.m6.application.AccesoEvaluacionDenegadoException;
import com.prisaly.m6.application.PreparacionContextoException;
import com.prisaly.m6.application.PreparacionContextoException.Codigo;
import com.prisaly.m6.application.integration.*;
import com.prisaly.m6.application.integration.ContextoAcademico.*;
import com.prisaly.m6.application.integration.IntentoEvaluacion.EstadoIntento;
import com.prisaly.m6.application.integration.IntentoEvaluacion.RespuestaReferencia;
import com.prisaly.m6.application.port.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class PrepararContextoEvaluacionServiceTest {
    private static final UUID ATTEMPT = UUID.randomUUID(), PARTICIPATION = UUID.randomUUID(), CASE = UUID.randomUUID();
    private static final UUID SCENARIO = UUID.randomUUID(), QUESTION = UUID.randomUUID(), OPTION = UUID.randomUUID();
    private final ConsultaIntentoEvaluacionPort attempts = mock(ConsultaIntentoEvaluacionPort.class);
    private final SolicitudCapacidadContextoAcademicoPort issuer = mock(SolicitudCapacidadContextoAcademicoPort.class);
    private final ConsultaContextoAcademicoPort contexts = mock(ConsultaContextoAcademicoPort.class);
    private final PrepararContextoEvaluacionService service = new PrepararContextoEvaluacionService(attempts, issuer, contexts);

    @Test
    void successfulPreparationPreservesDataAndCallsPortsInOrderWithoutReturningCapability() {
        var attempt = attempt(ATTEMPT, EstadoIntento.FINALIZADO, List.of(new RespuestaReferencia(QUESTION, OPTION)));
        var context = context(CASE, QUESTION, OPTION);
        when(attempts.findById(ATTEMPT)).thenReturn(Optional.of(attempt));
        when(issuer.solicitarCapacidad(ATTEMPT, CASE)).thenReturn("sensitive-fixture");
        when(contexts.findByCasoId(CASE, "sensitive-fixture")).thenReturn(Optional.of(context));
        var result = service.preparar(ATTEMPT);
        assertSame(attempt, result.intento()); assertSame(context, result.contextoAcademico());
        assertEquals(PARTICIPATION, result.intento().idParticipacion());
        assertFalse(result.toString().contains("sensitive-fixture"));
        var order = inOrder(attempts, issuer, contexts);
        order.verify(attempts).findById(ATTEMPT);
        order.verify(issuer).solicitarCapacidad(ATTEMPT, CASE);
        order.verify(contexts).findByCasoId(CASE, "sensitive-fixture");
        order.verifyNoMoreInteractions();
        assertEquals(2, ContextoEvaluacionPreparado.class.getRecordComponents().length);
        assertEquals(0, PrepararContextoEvaluacionService.class.getAnnotations().length);
    }

    @Test
    void missingInProgressOrInconsistentAttemptDoesNotRequestCapability() {
        when(attempts.findById(ATTEMPT)).thenReturn(Optional.empty());
        code(Codigo.INTENTO_INEXISTENTE, () -> service.preparar(ATTEMPT));
        when(attempts.findById(ATTEMPT)).thenReturn(Optional.of(attempt(ATTEMPT, EstadoIntento.EN_CURSO, List.of())));
        code(Codigo.INTENTO_NO_FINALIZADO, () -> service.preparar(ATTEMPT));
        when(attempts.findById(ATTEMPT)).thenReturn(Optional.of(attempt(UUID.randomUUID(), EstadoIntento.FINALIZADO, List.of())));
        code(Codigo.UUID_INTENTO_INCONSISTENTE, () -> service.preparar(ATTEMPT));
        verifyNoInteractions(issuer, contexts);
    }

    @Test
    void nullIdentifierNeverCallsProvidersAndAttemptFailureIsTechnical() {
        assertThrows(NullPointerException.class, () -> service.preparar(null));
        verifyNoInteractions(attempts, issuer, contexts);
        when(attempts.findById(ATTEMPT)).thenThrow(new IllegalStateException("unavailable"));
        code(Codigo.FALLO_CONSULTA_INTENTO, () -> service.preparar(ATTEMPT));
        verifyNoInteractions(issuer, contexts);
    }

    @Test
    void missingIssuerFailsClosedAfterAttemptChecks() {
        readyAttempt();
        code(Codigo.EMISOR_AUSENTE, () -> new PrepararContextoEvaluacionService(attempts, null, contexts).preparar(ATTEMPT));
        verifyNoInteractions(contexts);
    }

    @Test
    void unavailableOrDeniedIssuerStopsFlowWithoutLeakingDiagnostics() {
        readyAttempt();
        when(issuer.solicitarCapacidad(ATTEMPT, CASE)).thenThrow(new IllegalStateException("sensitive-fixture"));
        var error = code(Codigo.EMISOR_NO_DISPONIBLE, () -> service.preparar(ATTEMPT));
        assertSafe(error);
        doThrow(new AccesoEvaluacionDenegadoException(new RuntimeException("sensitive-fixture")))
                .when(issuer).solicitarCapacidad(ATTEMPT, CASE);
        assertSafe(assertThrows(AccesoEvaluacionDenegadoException.class, () -> service.preparar(ATTEMPT)));
        verifyNoInteractions(contexts);
    }

    @Test
    void nullEmptyAndWhitespaceCapabilitiesNeverReachAcademicProvider() {
        readyAttempt();
        for (String capability : new String[] {null, "", " "}) {
            when(issuer.solicitarCapacidad(ATTEMPT, CASE)).thenReturn(capability);
            code(Codigo.CAPACIDAD_INVALIDA, () -> service.preparar(ATTEMPT));
        }
        verifyNoInteractions(contexts);
    }

    @Test
    void deniedMissingAndFailedAcademicContextRemainDifferentOutcomes() {
        ready();
        when(contexts.findByCasoId(CASE, "sensitive-fixture")).thenReturn(Optional.empty());
        code(Codigo.CONTEXTO_INEXISTENTE, () -> service.preparar(ATTEMPT));
        when(contexts.findByCasoId(CASE, "sensitive-fixture")).thenThrow(
                new AccesoEvaluacionDenegadoException(new RuntimeException("sensitive-fixture")));
        assertSafe(assertThrows(AccesoEvaluacionDenegadoException.class, () -> service.preparar(ATTEMPT)));
        doThrow(new IllegalStateException("sensitive-fixture")).when(contexts).findByCasoId(CASE, "sensitive-fixture");
        assertSafe(code(Codigo.FALLO_CONSULTA_CONTEXTO, () -> service.preparar(ATTEMPT)));
    }

    @Test
    void wrongCaseQuestionOrSelectedOptionIsRejected() {
        ready();
        when(contexts.findByCasoId(CASE, "sensitive-fixture")).thenReturn(Optional.of(context(UUID.randomUUID(), QUESTION, OPTION)));
        code(Codigo.CASO_INCONSISTENTE, () -> service.preparar(ATTEMPT));
        when(contexts.findByCasoId(CASE, "sensitive-fixture")).thenReturn(Optional.of(context(CASE, UUID.randomUUID(), OPTION)));
        code(Codigo.REFERENCIAS_INCONSISTENTES, () -> service.preparar(ATTEMPT));
        when(contexts.findByCasoId(CASE, "sensitive-fixture")).thenReturn(Optional.of(context(CASE, QUESTION, UUID.randomUUID())));
        code(Codigo.REFERENCIAS_INCONSISTENTES, () -> service.preparar(ATTEMPT));
    }

    @Test
    void emptyAnswersAndOptionalRubricDoNotCreateCompletenessPolicies() {
        when(attempts.findById(ATTEMPT)).thenReturn(Optional.of(attempt(ATTEMPT, EstadoIntento.FINALIZADO, List.of())));
        when(issuer.solicitarCapacidad(ATTEMPT, CASE)).thenReturn("fixture");
        var bare = new ContextoAcademico(CASE, UUID.randomUUID(), "Case", null, null, 300, "Borrador", false,
                null, null, null, List.of(), null);
        when(contexts.findByCasoId(CASE, "fixture")).thenReturn(Optional.of(bare));
        assertSame(bare, service.preparar(ATTEMPT).contextoAcademico());
    }

    private void readyAttempt() {
        when(attempts.findById(ATTEMPT)).thenReturn(Optional.of(attempt(ATTEMPT, EstadoIntento.FINALIZADO,
                List.of(new RespuestaReferencia(QUESTION, OPTION)))));
    }

    private void ready() {
        readyAttempt(); when(issuer.solicitarCapacidad(ATTEMPT, CASE)).thenReturn("sensitive-fixture");
    }

    private static IntentoEvaluacion attempt(UUID id, EstadoIntento state, List<RespuestaReferencia> answers) {
        return new IntentoEvaluacion(id, PARTICIPATION, CASE, state, answers);
    }

    private static ContextoAcademico context(UUID caseId, UUID questionId, UUID optionId) {
        UUID rubricId = UUID.randomUUID();
        var option = new Opcion(optionId, questionId, "Option", false, null, 9);
        var question = new Pregunta(questionId, SCENARIO, "Question", null, "Borrador", false, 7,
                Instant.parse("2026-01-01T00:00:00.123456Z"), null, List.of(option));
        var scenario = new Escenario(SCENARIO, caseId, null, null, null, 3, false, List.of(question));
        var rubric = new Rubrica(rubricId, caseId, "Rubric", null, new BigDecimal("12.500"), "{\"x\":null}", null,
                Set.of(new RubricaPregunta(UUID.randomUUID(), rubricId, questionId, new BigDecimal("12.50"), null)));
        return new ContextoAcademico(caseId, UUID.randomUUID(), "Case", null, "Development", 300, "Publicado",
                false, null, null, null, List.of(scenario), rubric);
    }

    private static PreparacionContextoException code(Codigo expected, Runnable action) {
        var error = assertThrows(PreparacionContextoException.class, action::run);
        assertEquals(expected, error.codigo()); return error;
    }

    private static void assertSafe(RuntimeException error) {
        assertFalse(error.toString().contains("sensitive-fixture")); assertNull(error.getCause());
    }
}
