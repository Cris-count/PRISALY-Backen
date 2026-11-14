package com.prisaly.m3.presentation.dto;

/** El JSON de creación no cumple el contrato: campos, tipos o tiempo estimado. Se responde 400. */
public final class InvalidCasoRequestException extends RuntimeException {
    public InvalidCasoRequestException() {
        super("Invalid case creation request");
    }
}
