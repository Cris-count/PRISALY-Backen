package com.prisaly.m3.application.service;

import com.prisaly.m3.application.port.CasoPublicationPort;
import com.prisaly.m3.application.port.CasoCurrentUserPort;
import com.prisaly.m3.application.port.CasoAuthenticationException;
import com.prisaly.m3.application.update.CasoUpdateResult;
import java.util.Objects;
import java.util.UUID;

/** Separate persisted use case; the existing in-memory publication signature is unchanged. */
public final class CasoPersistentPublicationService {
    private final CasoPublicationPort publication;
    private final CasoCurrentUserPort identity;

    public CasoPersistentPublicationService(CasoPublicationPort publication) {
        this(publication, null);
    }

    public CasoPersistentPublicationService(CasoPublicationPort publication, CasoCurrentUserPort identity) {
        this.publication = Objects.requireNonNull(publication, "publication");
        this.identity = identity;
    }

    /** Trusted request identity only; never infer authorization from the supplied case UUID. */
    public CasoUpdateResult publish(UUID caseId, String expectedFingerprint) {
        Objects.requireNonNull(caseId, "caseId");
        UUID user;
        try { user = identity == null ? null : identity.currentUserId(); }
        catch (RuntimeException failure) { throw new CasoAuthenticationException(); }
        if (user == null) { throw new CasoAuthenticationException(); }
        return publication.publish(user.toString(), caseId, expectedFingerprint);
    }

    public CasoUpdateResult publish(String userId, UUID caseId, String expectedFingerprint) {
        return publication.publish(userId, caseId, expectedFingerprint);
    }
}
