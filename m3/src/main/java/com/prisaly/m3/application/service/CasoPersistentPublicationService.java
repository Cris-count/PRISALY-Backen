package com.prisaly.m3.application.service;

import com.prisaly.m3.application.port.CasoPublicationPort;
import com.prisaly.m3.application.update.CasoUpdateResult;
import java.util.Objects;
import java.util.UUID;

/** Separate persisted use case; the existing in-memory publication signature is unchanged. */
public final class CasoPersistentPublicationService {
    private final CasoPublicationPort publication;

    public CasoPersistentPublicationService(CasoPublicationPort publication) {
        this.publication = Objects.requireNonNull(publication, "publication");
    }

    public CasoUpdateResult publish(String userId, UUID caseId, String expectedFingerprint) {
        return publication.publish(userId, caseId, expectedFingerprint);
    }
}
