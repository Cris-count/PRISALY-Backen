package com.prisaly.m3.application.query;

import java.util.Objects;

/** Authorized projection and existing m3-v2 validator from the same committed snapshot. */
public record CasoDocenteFichaVersionada(CasoDocenteFicha ficha, String fingerprint) {
    public CasoDocenteFichaVersionada {
        Objects.requireNonNull(ficha, "ficha");
        if (fingerprint == null || !fingerprint.matches("m3-v2:[0-9a-f]{64}")) {
            throw new IllegalStateException("Invalid aggregate fingerprint returned by storage");
        }
    }
}
