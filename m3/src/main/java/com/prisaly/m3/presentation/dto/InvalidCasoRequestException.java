package com.prisaly.m3.presentation.dto;

public final class InvalidCasoRequestException extends RuntimeException {
    public InvalidCasoRequestException() {
        super("Invalid case creation request");
    }
}
