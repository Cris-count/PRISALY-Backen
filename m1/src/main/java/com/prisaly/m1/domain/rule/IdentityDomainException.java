package com.prisaly.m1.domain.rule;

public final class IdentityDomainException extends RuntimeException {

    private final String code;

    public IdentityDomainException(String code, String message) {
        super(message);
        if (code == null || code.isBlank()) {
            throw new IllegalArgumentException("code is required");
        }
        if (message == null || message.isBlank()) {
            throw new IllegalArgumentException("message is required");
        }
        this.code = code;
    }

    public String code() {
        return code;
    }
}
