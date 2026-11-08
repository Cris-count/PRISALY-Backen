package com.prisaly.m3.presentation;

import org.springframework.http.HttpHeaders;

/**
 * APP must supply this only after ratifying fingerprint transport with Architecture.
 * No default header, ETag interpretation or permissive production provider is installed.
 * The provider must reject missing, ambiguous or malformed preconditions.
 */
@FunctionalInterface
public interface CasoPatchConcurrencyContract {
    String expectedFingerprint(HttpHeaders headers);
}
