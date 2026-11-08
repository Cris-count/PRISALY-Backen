package com.prisaly.m3.presentation;

import java.util.List;
import org.springframework.http.HttpHeaders;

/**
 * Candidate single strong ETag/If-Match codec. Deliberately NOT a Spring component.
 * No new status codes or wildcard/list semantics are enabled institutionally.
 */
public final class CasoStrongEtagContract implements CasoEtagConcurrencyContract {
    private static final String FINGERPRINT = "m3-v2:[0-9a-f]{64}";

    @Override
    public String etag(String fingerprint) {
        if (fingerprint == null || !fingerprint.matches(FINGERPRINT)) {
            throw new IllegalStateException("Invalid storage fingerprint");
        }
        return '"' + fingerprint + '"';
    }

    @Override
    public String expectedFingerprint(HttpHeaders headers) {
        List<String> values = headers.get(HttpHeaders.IF_MATCH);
        if (values == null || values.size() != 1) { throw invalid(); }
        String value = values.getFirst();
        if (value == null) { throw invalid(); }
        // HTTP optional whitespace is SP / HTAB only, not arbitrary Unicode whitespace.
        value = value.replaceAll("^[ \\t]+|[ \\t]+$", "");
        if (!value.matches("\"" + FINGERPRINT + "\"")) { throw invalid(); }
        return value.substring(1, value.length() - 1);
    }

    private static IllegalArgumentException invalid() {
        return new IllegalArgumentException("A single strong aggregate If-Match validator is required");
    }
}
