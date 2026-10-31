package com.prisaly.m3.application.port;

import com.prisaly.m3.application.update.CasoUpdateCommand;
import com.prisaly.m3.application.update.CasoUpdateResult;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** M3-INC-10B, local M3 contract. Does not supersede shared architecture decisions. */
public interface CasoUpdatePort {
    /** Consistent committed aggregate and opaque precondition for a later command. */
    Optional<CasoUpdateResult> findById(UUID caseId);

    /** Atomic commands; omitted fields/elements remain untouched. No implicit deletion. */
    CasoUpdateResult update(UUID caseId, String expectedFingerprint, List<CasoUpdateCommand> commands);
}
