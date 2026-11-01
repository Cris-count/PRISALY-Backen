package com.prisaly.m4.presentation.dto;

/** Forma compartida con M1/M5. Sin SQL, credenciales ni timestamp exclusivo de M4. */
public record ErrorResponse(String code, String message) {
    public static ErrorResponse of(String code, String message) {
        return new ErrorResponse(code, message);
    }
}
