package com.prisaly.m3.application.port;

/** No authenticated institutional identity is available for this request. */
public final class CasoAuthenticationException extends RuntimeException {
    public CasoAuthenticationException() {
        super("Authentication required");
    }
}
