package com.prisaly.m1.api;

/**
 * Error de identidad con un código estable (por ejemplo AUTH_FAILED o USER_INACTIVE).
 * El manejador HTTP traduce ese código a 401, 403 o 400. No transporta la contraseña.
 */
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
