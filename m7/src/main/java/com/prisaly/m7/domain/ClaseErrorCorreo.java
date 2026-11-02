package com.prisaly.m7.domain;

/**
 * SMTP / delivery error classification (D5).
 * CONFIGURACION must not mark the recipient as Fallido.
 */
public enum ClaseErrorCorreo {
    PERMANENTE_DESTINATARIO,
    TRANSITORIO,
    CONFIGURACION
}
