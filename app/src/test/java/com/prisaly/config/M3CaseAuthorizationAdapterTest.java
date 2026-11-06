package com.prisaly.config;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.prisaly.m1.application.port.AuthenticatedUser;
import com.prisaly.m1.application.port.IdentityAccessPort;
import com.prisaly.m1.domain.rule.IdentityDomainException;
import com.prisaly.m3.application.port.CasoAuthenticationException;
import com.prisaly.m3.domain.rule.CasoDomainException;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class M3CaseAuthorizationAdapterTest {
    private final UUID currentId = UUID.randomUUID();
    private IdentityAccessPort identity;
    private M3CaseAuthorizationAdapter adapter;

    @BeforeEach
    void prepare() {
        identity = mock(IdentityAccessPort.class);
        when(identity.currentUser()).thenReturn(user("Activo"));
        adapter = new M3CaseAuthorizationAdapter(identity);
    }

    @Test
    void creationRequiresActualInstitutionalPermissionForTheAuthenticatedOwner() {
        when(identity.hasPermission(currentId, "CREAR_CASO")).thenReturn(true);
        assertEquals(currentId, adapter.currentUserId());
        assertTrue(adapter.canCreateCase(currentId.toString(), currentId.toString()));
        verify(identity).hasPermission(currentId, "CREAR_CASO");
    }

    @Test
    void deniesMissingPermissionAndMismatchedOrMissingIdentities() {
        assertFalse(adapter.canCreateCase(currentId.toString(), currentId.toString()));
        clearInvocations(identity);
        String other = UUID.randomUUID().toString(); // Negative impersonation input, never a persisted owner.
        assertFalse(adapter.canCreateCase(other, currentId.toString()));
        assertFalse(adapter.canCreateCase(currentId.toString(), other));
        assertFalse(adapter.canCreateCase(null, currentId.toString()));
        assertFalse(adapter.canCreateCase(currentId.toString(), "not-a-uuid"));
        verify(identity, never()).hasPermission(any(), any());
    }

    @Test
    void modificationAndPublicationRemainDeniedWithoutConsultingOrInferringPermissions() {
        assertFalse(adapter.canModifyCase(currentId.toString(), UUID.randomUUID().toString()));
        assertFalse(adapter.canPublishCase(currentId.toString(), UUID.randomUUID().toString()));
        verifyNoInteractions(identity);
    }

    @Test
    void rejectsUnauthenticatedInactiveAndMissingAccountsWithoutUsingPermission() {
        when(identity.currentUser()).thenThrow(new IdentityDomainException("UNAUTHENTICATED", "fixture"));
        assertThrows(CasoAuthenticationException.class, adapter::currentUserId);
        doReturn(user("Inactivo")).when(identity).currentUser();
        assertEquals("FORBIDDEN", assertThrows(CasoDomainException.class, adapter::currentUserId).code());
        doReturn(null).when(identity).currentUser();
        assertThrows(CasoAuthenticationException.class, adapter::currentUserId);
        verify(identity, never()).hasPermission(any(), any());
    }

    @Test
    void persistenceFailureFromM1IsPropagatedInsteadOfBecomingPermissionOrAbsence() {
        RuntimeException failure = new IllegalStateException("Identity persistence unavailable");
        when(identity.hasPermission(currentId, "CREAR_CASO")).thenThrow(failure);
        assertSame(failure, assertThrows(RuntimeException.class,
                () -> adapter.canCreateCase(currentId.toString(), currentId.toString())));
    }

    private AuthenticatedUser user(String status) {
        return new AuthenticatedUser(currentId, "docente@prisaly.test", "Docente fixture",
                "Docente", status, Set.of());
    }

    @Test
    void creationPermissionDoesNotAuthorizeAnotherTeacherOrAnInvalidCaller() {
        when(identity.hasPermission(currentId, "CREAR_CASO")).thenReturn(true);
        assertFalse(adapter.canCreateCase(currentId.toString(), UUID.randomUUID().toString()));
        for (String caller : new String[] {null, "", "not-a-uuid", " " + currentId,
                UUID.randomUUID().toString()}) {
            assertFalse(adapter.canCreateCase(caller, currentId.toString()));
        }
        assertFalse(adapter.canCreateCase(currentId.toString(), null));
        verify(identity, never()).hasPermission(any(), any());
    }

    @Test
    void absentOrMalformedAuthenticatedSubjectCannotAuthorizeCreation() {
        doReturn(null).when(identity).currentUser();
        assertThrows(CasoAuthenticationException.class,
                () -> adapter.canCreateCase(currentId.toString(), currentId.toString()));
        doReturn(new AuthenticatedUser(null, "fixture@prisaly.test", "Fixture", "Docente",
                "Activo", Set.of("CREAR_CASO"))).when(identity).currentUser();
        assertThrows(CasoAuthenticationException.class,
                () -> adapter.canCreateCase(currentId.toString(), currentId.toString()));
        for (String code : new String[] {"USER_NOT_FOUND", "USER_INACTIVE", "FORBIDDEN"}) {
            doThrow(new IdentityDomainException(code, "fixture")).when(identity).currentUser();
            assertEquals("FORBIDDEN", assertThrows(CasoDomainException.class,
                    () -> adapter.canCreateCase(currentId.toString(), currentId.toString())).code());
        }
        verify(identity, never()).hasPermission(any(), any());
    }

    @Test
    void institutionalPermissionLookupFailuresNeverBecomeAuthorization() {
        for (String code : new String[] {"USER_NOT_FOUND", "USER_INACTIVE", "FORBIDDEN"}) {
            doThrow(new IdentityDomainException(code, "fixture"))
                    .when(identity).hasPermission(currentId, "CREAR_CASO");
            assertEquals("FORBIDDEN", assertThrows(CasoDomainException.class,
                    () -> adapter.canCreateCase(currentId.toString(), currentId.toString())).code());
        }
        doThrow(new IdentityDomainException("UNAUTHENTICATED", "fixture"))
                .when(identity).hasPermission(currentId, "CREAR_CASO");
        assertThrows(CasoAuthenticationException.class,
                () -> adapter.canCreateCase(currentId.toString(), currentId.toString()));
    }

    @Test
    void cachedPermissionCodesDoNotReplaceTheInstitutionalPermissionCheck() {
        when(identity.currentUser()).thenReturn(new AuthenticatedUser(currentId,
                "docente@prisaly.test", "Docente fixture", "Docente", "Activo", Set.of("CREAR_CASO")));
        when(identity.hasPermission(currentId, "CREAR_CASO")).thenReturn(false);
        assertFalse(adapter.canCreateCase(currentId.toString(), currentId.toString()));
        verify(identity).hasPermission(currentId, "CREAR_CASO");
    }
}
