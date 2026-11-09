package com.prisaly.m3.application.port;

import com.prisaly.m3.application.update.CasoUpdateResult;
import java.util.UUID;

/** Local M3 operation. A joined caller transaction still owns the final commit. */
public interface CasoPublicationPort {
    CasoUpdateResult publish(String userId, UUID caseId, String expectedFingerprint);

    /** PD-02: Publicado to Archivado only. */
    CasoUpdateResult archive(String userId, UUID caseId, String expectedFingerprint);
}
