package com.prisaly.m4.presentation.dto;

import java.time.LocalDateTime;

/**
 * DTO de error estándar de PRISALY.
 */
public record ErrorResponse(
        String codigo,
        String mensaje,
        LocalDateTime timestamp
) {
    public static ErrorResponse of(String codigo, String mensaje) {
        return new ErrorResponse(codigo, mensaje, LocalDateTime.now());
    }
}
