package com.prisaly.config;

import com.prisaly.m1.application.port.AuthenticatedUser;
import com.prisaly.m1.application.port.IdentityAccessPort;
import com.prisaly.m1.domain.rule.IdentityDomainException;
import com.prisaly.m3.application.port.CaseAuthorizationPort;
import com.prisaly.m3.application.port.CasoAuthenticationException;
import com.prisaly.m3.application.port.CasoCurrentUserPort;
import com.prisaly.m3.domain.rule.CasoDomainException;
import java.util.Objects;
import java.util.UUID;

/** Real M1 permissions for creation, modification (D6), publication and archiving (PD-02). */
public final class M3CaseAuthorizationAdapter implements CaseAuthorizationPort, CasoCurrentUserPort {
    private final IdentityAccessPort identity;

    public M3CaseAuthorizationAdapter(IdentityAccessPort identity) {
        this.identity = Objects.requireNonNull(identity);
    }

    @Override
    public UUID currentUserId() {
        return currentUser().userId();
    }

    @Override
    public boolean canCreateCase(String userId, String docenteId) {
        AuthenticatedUser current = currentUser();
        if (!matches(current.userId(), userId) || !matches(current.userId(), docenteId)) {
            return false;
        }
        try {
            return identity.hasPermission(current.userId(), "CREAR_CASO");
        } catch (IdentityDomainException ex) {
            throw translate(ex);
        }
    }

    @Override
    public boolean canModifyCase(String userId, String caseId) {
        AuthenticatedUser current = currentUser();
        if (!matches(current.userId(), userId)) {
            return false;
        }
        try {
            // PD-01: editing needs both independent permissions. Ownership and state are enforced
            // by the M3 use case against the stored aggregate.
            return identity.hasPermission(current.userId(), "MODIFICAR_CASO")
                    && identity.hasPermission(current.userId(), "CONSULTAR_CASO");
        } catch (IdentityDomainException ex) {
            throw translate(ex);
        }
    }

    @Override
    public boolean canPublishCase(String userId, String caseId) {
        return tienePermiso(userId, "PUBLICAR_CASO");
    }

    @Override
    public boolean canArchiveCase(String userId, String caseId) {
        return tienePermiso(userId, "ARCHIVAR_CASO");
    }

    /** PD-02: no default grants; ownership and state are enforced by the M3 transition. */
    private boolean tienePermiso(String userId, String codigo) {
        AuthenticatedUser current = currentUser();
        if (!matches(current.userId(), userId)) {
            return false;
        }
        try {
            return identity.hasPermission(current.userId(), codigo);
        } catch (IdentityDomainException ex) {
            throw translate(ex);
        }
    }

    private AuthenticatedUser currentUser() {
        final AuthenticatedUser current;
        try {
            current = identity.currentUser();
        } catch (IdentityDomainException ex) {
            throw translate(ex);
        }
        if (current == null || current.userId() == null) { throw new CasoAuthenticationException(); }
        if (!"Activo".equalsIgnoreCase(current.accountStatus())) {
            throw new CasoDomainException("FORBIDDEN", "Case creation is not authorized");
        }
        return current;
    }

    private static boolean matches(UUID authenticated, String supplied) {
        // Canonical UUID representation only, without trusting a caller-supplied identity.
        return supplied != null && authenticated.toString().equalsIgnoreCase(supplied);
    }

    private static RuntimeException translate(IdentityDomainException ex) {
        return switch (ex.code()) {
            case "UNAUTHENTICATED" -> new CasoAuthenticationException();
            case "USER_INACTIVE", "USER_NOT_FOUND", "FORBIDDEN" ->
                    new CasoDomainException("FORBIDDEN", "Case creation is not authorized");
            default -> ex; // Unknown institutional/technical failures must not become authorization.
        };
    }
}
