package com.prisaly.m3;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.prisaly.m3.application.port.*;
import com.prisaly.m3.application.port.AutorizacionConsultaCasoPort.Decision;
import com.prisaly.m3.application.service.CasoDocenteConsultaService;
import com.prisaly.m3.domain.model.*;
import com.prisaly.m3.domain.rule.CasoDomainException;
import com.prisaly.m3.presentation.dto.CasoDocenteConsultaResponse;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class CasoDocenteConsultaServiceTest {
    private final UUID id = UUID.randomUUID();
    private final UUID owner = UUID.randomUUID();
    private final CasoCurrentUserPort identity = mock(CasoCurrentUserPort.class);
    private final AutorizacionConsultaCasoPort auth = mock(AutorizacionConsultaCasoPort.class);
    private final CasoRepositoryPort repository = mock(CasoRepositoryPort.class);
    private final CasoDocenteConsultaService service = new CasoDocenteConsultaService(identity, auth, repository);

    private Caso caso(UUID caseId, UUID teacher, CasoEstado state) {
        return new Caso(caseId.toString(), "Ficha", teacher.toString(), 300, state, List.of(), null, false);
    }

    private void eligible() {
        when(identity.currentUserId()).thenReturn(owner);
        when(auth.verificarAcceso(owner, id)).thenReturn(Decision.AUTORIZADO);
    }

    private void denied(Runnable operation) {
        assertEquals(CasoDocenteConsultaService.ACCESO_DENEGADO,
                assertThrows(CasoDomainException.class, operation::run).code());
    }

    @Test
    void malformedOrShortUuidNeverTouchesIdentityAuthorizationOrRepository() {
        for (String value : new String[] {null, "", "invalid", "1-1-1-1-1"}) {
            assertThrows(IllegalArgumentException.class, () -> service.consultar(value));
        }
        verifyNoInteractions(identity, auth, repository);
    }

    @Test
    void missingOrFailedIdentityNeverReads() {
        assertThrows(CasoAuthenticationException.class, () -> service.consultar(id.toString()));
        assertThrows(CasoAuthenticationException.class,
                () -> new CasoDocenteConsultaService(null, auth, repository).consultar(id.toString()));
        var failure = new IllegalStateException("private identity");
        when(identity.currentUserId()).thenThrow(failure);
        assertEquals("CASE_AUTHORIZATION_UNAVAILABLE", assertThrows(CasoDomainException.class,
                () -> service.consultar(id.toString())).code());
        verifyNoInteractions(auth, repository);
    }

    @Test
    void missingProviderNeverReads() {
        when(identity.currentUserId()).thenReturn(owner);
        denied(() -> new CasoDocenteConsultaService(identity, null, repository).consultar(id.toString()));
        verifyNoInteractions(repository);
    }

    @Test
    void deniedEligibilityNeverReads() {
        when(identity.currentUserId()).thenReturn(owner);
        for (Decision decision : new Decision[] {Decision.DENEGADO}) {
            when(auth.verificarAcceso(owner, id)).thenReturn(decision);
            denied(() -> service.consultar(id.toString()));
        }
        when(auth.verificarAcceso(owner, id)).thenThrow(new IllegalStateException("private policy"));
        var error = assertThrows(CasoDomainException.class, () -> service.consultar(id.toString()));
        assertEquals("CASE_AUTHORIZATION_UNAVAILABLE", error.code());
        assertNull(error.getCause());
        assertFalse(error.toString().contains("private policy"));
        verifyNoInteractions(repository);
    }

    @Test
    void unavailableOrNullDecisionNeverGrantsAccessOrLeaksContent() {
        when(identity.currentUserId()).thenReturn(owner);
        for (Decision decision : new Decision[] {Decision.NO_DISPONIBLE, null}) {
            when(auth.verificarAcceso(owner, id)).thenReturn(decision);
            assertEquals("CASE_AUTHORIZATION_UNAVAILABLE", assertThrows(CasoDomainException.class,
                    () -> service.consultar(id.toString())).code());
        }
        verifyNoInteractions(repository);
        eligible();
        when(repository.findById(id.toString())).thenReturn(Optional.of(caso(id, owner, CasoEstado.BORRADOR)));
        for (Decision decision : new Decision[] {Decision.NO_DISPONIBLE, null}) {
            when(auth.verificarVisibilidad(owner, id, CasoEstado.BORRADOR, true)).thenReturn(decision);
            assertEquals("CASE_AUTHORIZATION_UNAVAILABLE", assertThrows(CasoDomainException.class,
                    () -> service.consultar(id.toString())).code());
        }
    }

    @Test
    void authorizedMissingCaseDiffersFromDenial() {
        eligible();
        when(repository.findById(id.toString())).thenReturn(Optional.empty());
        assertEquals(CasoDocenteConsultaService.CASO_INEXISTENTE,
                assertThrows(CasoDomainException.class, () -> service.consultar(id.toString())).code());
        verify(auth, never()).verificarVisibilidad(any(), any(), any(), anyBoolean());
    }

    @Test
    void technicalFailureIsNotAbsenceOrDenial() {
        eligible();
        var failure = new IllegalStateException("database failure");
        when(repository.findById(id.toString())).thenThrow(failure);
        assertSame(failure, assertThrows(IllegalStateException.class, () -> service.consultar(id.toString())));
    }

    @Test
    void anotherOwnerIsDeniedEvenWithGeneralEligibility() {
        eligible();
        when(repository.findById(id.toString())).thenReturn(Optional.of(caso(id, UUID.randomUUID(), CasoEstado.PUBLICADO)));
        denied(() -> service.consultar(id.toString()));
        verify(auth, never()).verificarVisibilidad(any(), any(), any(), anyBoolean());
    }

    @Test
    void mismatchedRepositoryIdentityIsAnIntegrityFailure() {
        eligible();
        when(repository.findById(id.toString())).thenReturn(Optional.of(caso(UUID.randomUUID(), owner, CasoEstado.BORRADOR)));
        assertThrows(IllegalStateException.class, () -> service.consultar(id.toString()));
    }

    @Test
    void ownershipAndPublishedStateNeverReplaceVisibilityAuthorization() {
        eligible();
        for (CasoEstado state : CasoEstado.values()) {
            when(repository.findById(id.toString())).thenReturn(Optional.of(caso(id, owner, state)));
            for (Decision decision : new Decision[] {Decision.DENEGADO}) {
                when(auth.verificarVisibilidad(owner, id, state, true)).thenReturn(decision);
                denied(() -> service.consultar(id.toString()));
            }
        }
    }

    @Test
    void failedVisibilityIsSanitized() {
        eligible();
        when(repository.findById(id.toString())).thenReturn(Optional.of(caso(id, owner, CasoEstado.BORRADOR)));
        when(auth.verificarVisibilidad(owner, id, CasoEstado.BORRADOR, true))
                .thenThrow(new IllegalStateException("private visibility"));
        var error = assertThrows(CasoDomainException.class, () -> service.consultar(id.toString()));
        assertEquals("CASE_AUTHORIZATION_UNAVAILABLE", error.code());
        assertNull(error.getCause());
        assertFalse(error.toString().contains("private visibility"));
    }

    @Test
    void controlledPolicyProducesOnlyMinimalFieldsAfterOneOrderedRead() {
        eligible();
        when(repository.findById(id.toString())).thenReturn(Optional.of(caso(id, owner, CasoEstado.BORRADOR)));
        when(auth.verificarVisibilidad(owner, id, CasoEstado.BORRADOR, true)).thenReturn(Decision.AUTORIZADO);
        // Controlled test policy only; not an institutional grant for drafts.
        var response = CasoDocenteConsultaResponse.from(service.consultar(id.toString().toUpperCase(java.util.Locale.ROOT)));
        assertEquals(new CasoDocenteConsultaResponse(id, "Ficha", 300, "BORRADOR"), response);
        var order = inOrder(identity, auth, repository);
        order.verify(identity).currentUserId();
        order.verify(auth).verificarAcceso(owner, id);
        order.verify(repository).findById(id.toString());
        order.verify(auth).verificarVisibilidad(owner, id, CasoEstado.BORRADOR, true);
        order.verifyNoMoreInteractions();
        assertEquals(List.of("idCaso", "titulo", "tiempoEstimadoSegundos", "estado"), Arrays.stream(
                CasoDocenteConsultaResponse.class.getRecordComponents()).map(java.lang.reflect.RecordComponent::getName).toList());
        assertEquals(0, CasoDocenteConsultaService.class.getAnnotations().length);
    }
}
