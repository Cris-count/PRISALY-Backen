package com.prisaly.m3;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.prisaly.m3.application.port.*;
import com.prisaly.m3.application.service.CasoPersistentUpdateService;
import com.prisaly.m3.domain.model.*;
import com.prisaly.m3.domain.rule.CasoDomainException;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class CasoPersistentUpdateServiceTest {
    private final UUID id = UUID.randomUUID();
    private final UUID owner = UUID.randomUUID();
    private final CasoCurrentUserPort identity = mock(CasoCurrentUserPort.class);
    private final CaseAuthorizationPort auth = mock(CaseAuthorizationPort.class);
    private final CasoRepositoryPort repository = mock(CasoRepositoryPort.class);
    private final CasoPersistentUpdateService service = new CasoPersistentUpdateService(identity, auth, repository);
    private final String fingerprint = "m3-v2:" + "0".repeat(64);

    private Caso caso(UUID teacher, boolean locked) {
        return new Caso(id.toString(), "Draft", teacher.toString(), 300, CasoEstado.BORRADOR, List.of(), null, locked);
    }

    private void authorize() {
        when(identity.currentUserId()).thenReturn(owner);
        when(auth.canModifyCase(owner.toString(), id.toString())).thenReturn(true);
    }

    @Test
    void controlledAuthorizationAndOwnershipPrecedeWriteWithUnchangedPrecondition() {
        authorize();
        var replacement = caso(owner, false);
        when(repository.findById(id.toString())).thenReturn(Optional.of(replacement));
        service.update(id, fingerprint, replacement);
        var order = inOrder(identity, auth, repository);
        order.verify(identity).currentUserId();
        order.verify(auth).canModifyCase(owner.toString(), id.toString());
        order.verify(repository).findById(id.toString());
        order.verify(repository).update(replacement, fingerprint);
        order.verifyNoMoreInteractions();
    }

    @Test
    void missingOrFailedIdentityNeverTouchesAuthorizationOrPersistence() {
        assertThrows(CasoAuthenticationException.class, () -> service.update(id, fingerprint, caso(owner, false)));
        var failure = new IllegalStateException("private identity");
        when(identity.currentUserId()).thenThrow(failure);
        assertEquals("CASE_AUTHORIZATION_UNAVAILABLE", assertThrows(CasoDomainException.class,
                () -> service.update(id, fingerprint, caso(owner, false))).code());
        verifyNoInteractions(auth, repository);
    }

    @Test
    void missingDeniedOrTechnicalAuthorizationNeverTouchesRepository() {
        when(identity.currentUserId()).thenReturn(owner);
        assertThrows(CasoDomainException.class, () -> service.update(id, fingerprint, caso(owner, false)));
        assertThrows(CasoDomainException.class, () -> new CasoPersistentUpdateService(identity, null, repository)
                .update(id, fingerprint, caso(owner, false)));
        when(auth.canModifyCase(owner.toString(), id.toString())).thenThrow(new IllegalStateException("private policy"));
        var error = assertThrows(CasoDomainException.class, () -> service.update(id, fingerprint, caso(owner, false)));
        assertEquals("CASE_AUTHORIZATION_UNAVAILABLE", error.code());
        assertNull(error.getCause());
        assertFalse(error.toString().contains("private policy"));
        verifyNoInteractions(repository);
    }

    @Test
    void foreignOwnerAndLockedCaseNeverWrite() {
        authorize();
        when(repository.findById(id.toString())).thenReturn(Optional.of(caso(UUID.randomUUID(), false)));
        assertEquals("FORBIDDEN", assertThrows(CasoDomainException.class,
                () -> service.update(id, fingerprint, caso(owner, false))).code());
        when(repository.findById(id.toString())).thenReturn(Optional.of(caso(owner, true)));
        assertEquals("EVALUABLE_CONTENT_LOCKED", assertThrows(CasoDomainException.class,
                () -> service.update(id, fingerprint, caso(owner, false))).code());
        verify(repository, never()).update(any(), anyString());
    }

    @Test
    void missingCaseAndTechnicalFailureAreDistinctAndNeverWrite() {
        authorize();
        assertEquals("CASE_NOT_FOUND", assertThrows(CasoDomainException.class,
                () -> service.update(id, fingerprint, caso(owner, false))).code());
        var failure = new IllegalStateException("database failure");
        when(repository.findById(id.toString())).thenThrow(failure);
        assertSame(failure, assertThrows(IllegalStateException.class,
                () -> service.update(id, fingerprint, caso(owner, false))));
        verify(repository, never()).update(any(), anyString());
    }

    @Test
    void replacementCannotChangeOwnerBeforeRepositoryWrite() {
        authorize();
        when(repository.findById(id.toString())).thenReturn(Optional.of(caso(owner, false)));
        assertThrows(IllegalArgumentException.class, () -> service.update(id, fingerprint, caso(UUID.randomUUID(), false)));
        verify(repository, never()).update(any(), anyString());
    }
}
