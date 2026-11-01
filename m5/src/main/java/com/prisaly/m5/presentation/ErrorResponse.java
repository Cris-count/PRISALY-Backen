package com.prisaly.m5.presentation;

/**
 * Cuerpo de error HTTP de M5.
 *
 * <p>Sigue la forma vigente en develop: {@code {"code", "message"}} de M1 y
 * de las respuestas 401/403 de la cadena de seguridad compartida. M4 usa
 * otra forma ({@code codigo, mensaje, timestamp}); la unificación es una
 * decisión pendiente del equipo (ver m5/docs).
 */
public record ErrorResponse(String code, String message) {
}
