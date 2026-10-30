package com.prisaly.m3.domain.rule;

/**
 * Identifiable academic rule violation for case management.
 */
public final class CasoDomainException extends RuntimeException {

    private final String code;

    public CasoDomainException(String code, String message) {
        super(message);
        if (code == null || code.isBlank()) {
            throw new IllegalArgumentException("CasoDomainException.code is required");
        }
        if (message == null || message.isBlank()) {
            throw new IllegalArgumentException("CasoDomainException.message is required");
        }
        this.code = code;
    }

    public String code() {
        return code;
    }
}
