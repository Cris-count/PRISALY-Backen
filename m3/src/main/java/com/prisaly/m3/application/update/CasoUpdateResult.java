package com.prisaly.m3.application.update;

import com.prisaly.m3.domain.model.Caso;
import java.util.Objects;

/** The fingerprint is a concurrency precondition, never an authorization credential. */
public record CasoUpdateResult(Caso caso, String fingerprint) {
    public CasoUpdateResult {
        Objects.requireNonNull(caso, "caso");
        Objects.requireNonNull(fingerprint, "fingerprint");
    }
}
