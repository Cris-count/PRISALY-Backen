package com.prisaly.m3.application.service;

import com.prisaly.m3.application.port.*;
import com.prisaly.m3.domain.model.Caso;
import com.prisaly.m3.domain.rule.CasoDomainException;
import com.prisaly.m3.domain.rule.ContenidoEvaluableLock;
import java.util.Objects;
import java.util.UUID;

/** Protected update use case; authorization providers are supplied by the institutional composition. */
public final class CasoPersistentUpdateService {
    private final CasoCurrentUserPort identity;
    private final CaseAuthorizationPort authorization;
    private final CasoRepositoryPort repository;

    public CasoPersistentUpdateService(CasoCurrentUserPort identity, CaseAuthorizationPort authorization,
            CasoRepositoryPort repository) {
        this.identity = identity;
        this.authorization = authorization;
        this.repository = Objects.requireNonNull(repository, "repository");
    }

    public void update(UUID caseId, String fingerprint, Caso replacement) {
        Objects.requireNonNull(replacement, "replacement");
        updateFields(caseId, fingerprint, stored -> replacement);
    }

    /** Apply a sparse request only after identity, authorization, ownership and lock checks. */
    public void updateFields(UUID caseId, String fingerprint, java.util.function.UnaryOperator<Caso> fields) {
        updateFieldsAuthorized(caseId, () -> fingerprint, fields);
    }

    public void updateFieldsAuthorized(UUID caseId, java.util.function.Supplier<String> fingerprint,
            java.util.function.UnaryOperator<Caso> fields) {
        Objects.requireNonNull(caseId, "caseId");
        Objects.requireNonNull(fields, "fields");
        Objects.requireNonNull(fingerprint, "fingerprint");
        UUID user;
        try { user = identity == null ? null : identity.currentUserId(); }
        catch (CasoAuthenticationException | CasoDomainException classified) { throw classified; }
        catch (RuntimeException technicalFailure) { throw unavailable(); }
        if (user == null) { throw new CasoAuthenticationException(); }
        boolean allowed;
        try { allowed = authorization != null && authorization.canModifyCase(user.toString(), caseId.toString()); }
        catch (CasoDomainException classified) {
            if (CasoApplicationService.CODE_FORBIDDEN.equals(classified.code())) { throw denied(); }
            throw unavailable();
        }
        catch (RuntimeException technicalFailure) { throw unavailable(); }
        if (!allowed) { throw denied(); }
        var stored = repository.findById(caseId.toString()).orElseThrow(
                () -> new CasoDomainException("CASE_NOT_FOUND", "Case does not exist"));
        if (!caseId.toString().equalsIgnoreCase(stored.id())) { throw new IllegalStateException("Repository case mismatch"); }
        if (!user.toString().equalsIgnoreCase(stored.docenteId())) { throw denied(); }
        ContenidoEvaluableLock.assertDraftEditable(stored);
        String expected = fingerprint.get();
        Caso replacement = Objects.requireNonNull(fields.apply(stored));
        if (!stored.id().equals(replacement.id()) || !stored.docenteId().equals(replacement.docenteId())) {
            throw new IllegalArgumentException("Replacement must preserve case identity and owner");
        }
        repository.update(replacement, expected);
    }

    private static CasoDomainException unavailable() {
        return new CasoDomainException("CASE_AUTHORIZATION_UNAVAILABLE", "Institutional verification unavailable");
    }

    private static CasoDomainException denied() {
        return new CasoDomainException(CasoApplicationService.CODE_FORBIDDEN, "Case modification denied");
    }
}
