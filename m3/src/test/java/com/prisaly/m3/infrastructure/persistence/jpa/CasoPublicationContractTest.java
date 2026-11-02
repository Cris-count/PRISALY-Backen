package com.prisaly.m3.infrastructure.persistence.jpa;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.prisaly.m3.application.port.CaseAuthorizationPort;
import com.prisaly.m3.application.port.CasoPublicationPort;
import com.prisaly.m3.application.service.CasoPersistentPublicationService;
import com.prisaly.m3.application.update.CasoUpdateResult;
import com.prisaly.m3.domain.rule.CasoDomainException;
import com.prisaly.m3.infrastructure.persistence.jpa.CasoJpaAggregateSnapshot.*;
import jakarta.persistence.EntityManagerFactory;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.orm.jpa.JpaTransactionManager;

class CasoPublicationContractTest {
    private static final UUID ID = UUID.randomUUID();
    private static final Instant DATE = Instant.parse("2026-01-01T00:00:00Z");

    @Test
    void invalidParametersFailBeforeTransactionOrAuthorization() {
        var emf = mock(EntityManagerFactory.class);
        var authorization = mock(CaseAuthorizationPort.class);
        var adapter = new CasoPublicationAdapter(emf, new JpaTransactionManager(emf), authorization);
        assertThrows(IllegalArgumentException.class, () -> adapter.publish(null, ID, "bad"));
        assertThrows(IllegalArgumentException.class, () -> adapter.publish(" ", ID, "bad"));
        assertThrows(NullPointerException.class, () -> adapter.publish("user", null, "bad"));
        assertThrows(IllegalArgumentException.class, () -> adapter.publish("user", ID, null));
        assertThrows(IllegalArgumentException.class, () -> adapter.publish("user", ID, "m3-v2:bad"));
        code("STALE_CASE", () -> adapter.publish("user", ID, "m3-v1:" + "0".repeat(64)));
        verifyNoInteractions(authorization);
        verify(emf, never()).createEntityManager();
    }

    @Test
    void missingCaseAndDeniedAuthorizationUseExistingErrors() {
        code("CASE_NOT_FOUND", () -> CasoPublicationAdapter.requireExisting(null));
        var authorization = mock(CaseAuthorizationPort.class);
        code("FORBIDDEN", () -> CasoPublicationAdapter.assertAuthorized(authorization, "user", ID));
        verify(authorization).canPublishCase("user", ID.toString());
        verifyNoMoreInteractions(authorization);
    }

    @Test
    void staleFingerprintPrecedesStateAndAcademicErrors() {
        var snapshot = snapshot("Archivado", DATE, true, true);
        code("STALE_CASE", () -> CasoPublicationAdapter.validatePublication(snapshot, "m3-v2:" + "0".repeat(64)));
    }

    @Test
    void statesAndLockAreRejectedBeforeRubricValidation() {
        reject("ALREADY_PUBLISHED", snapshot("Publicado", null, true, true));
        reject("ARCHIVED_CASE_OPERATION_PENDING", snapshot("Archivado", DATE, true, true));
        reject("EVALUABLE_CONTENT_LOCKED", snapshot("Publicado", DATE, true, true));
    }

    @Test
    void existingQuestionRulesRemainInForceAndStructuralRubricIsNotComplete() {
        reject("CASE_INCOMPLETE", snapshot("Borrador", null, false, false));
        reject("INVALID_QUESTION", snapshot("Borrador", null, true, false));
        reject("INCOMPLETE_RUBRIC", snapshot("Borrador", null, true, true));
    }

    @Test
    void applicationDelegatesOnceWithoutCallingLegacyPublication() {
        var port = mock(CasoPublicationPort.class);
        var source = snapshot("Borrador", null, true, true);
        var result = new CasoUpdateResult(CasoPersistenceMapper.toDomain(source), CasoAggregateFingerprint.of(source));
        when(port.publish("user", ID, result.fingerprint())).thenReturn(result);
        assertSame(result, new CasoPersistentPublicationService(port).publish("user", ID, result.fingerprint()));
        verify(port).publish("user", ID, result.fingerprint());
        verifyNoMoreInteractions(port);
    }

    private static void reject(String expected, CasoJpaAggregateSnapshot snapshot) {
        code(expected, () -> CasoPublicationAdapter.validatePublication(snapshot, CasoAggregateFingerprint.of(snapshot)));
    }

    private static void code(String expected, Runnable action) {
        assertEquals(expected, assertThrows(CasoDomainException.class, action::run).code());
    }

    private static CasoJpaAggregateSnapshot snapshot(String state, Instant lock, boolean question, boolean option) {
        UUID scenario = UUID.randomUUID();
        UUID questionId = UUID.randomUUID();
        UUID rubric = UUID.randomUUID();
        return new CasoJpaAggregateSnapshot(new CasoRow(ID, UUID.randomUUID(), "Case", null, null,
                300, state, true, lock, DATE, null),
                List.of(new EscenarioRow(scenario, ID, null, null, null, 1, true)),
                question ? List.of(new PreguntaRow(questionId, scenario, "Question", null,
                        "Borrador", true, 1, DATE, null)) : List.of(),
                question && option ? List.of(new OpcionRespuestaRow(UUID.randomUUID(), questionId,
                        "Correct", true, null, 1)) : List.of(),
                new RubricaRow(rubric, ID, "Structural rubric", null, new BigDecimal("12.50"), "{}", DATE),
                question ? Set.of(new RubricaPreguntaRow(UUID.randomUUID(), rubric, questionId,
                        new BigDecimal("12.50"), null)) : Set.of());
    }
}
