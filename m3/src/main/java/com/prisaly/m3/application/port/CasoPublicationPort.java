package com.prisaly.m3.application.port;

import com.prisaly.m3.application.update.CasoUpdateResult;
import java.util.UUID;
import java.util.function.Supplier;

/** Local M3 operation. A joined caller transaction still owns the final commit. */
public interface CasoPublicationPort {
    CasoUpdateResult publish(String userId, UUID caseId, String expectedFingerprint);

    /** PD-02: Publicado to Archivado only. */
    CasoUpdateResult archive(String userId, UUID caseId, String expectedFingerprint);

    /**
     * N1/F4-H1: the precondition is resolved only after permission and ownership have been verified, so a
     * non-owner is answered 403 regardless of the validator it sends. Adapters override it lazily.
     */
    default CasoUpdateResult publishResolvingPrecondition(String userId, UUID caseId, Supplier<String> expectedFingerprint) {
        return publish(userId, caseId, expectedFingerprint.get());
    }

    /** N1/F4-H1: same ordering guarantee as {@link #publishResolvingPrecondition}. */
    default CasoUpdateResult archiveResolvingPrecondition(String userId, UUID caseId, Supplier<String> expectedFingerprint) {
        return archive(userId, caseId, expectedFingerprint.get());
    }
}
