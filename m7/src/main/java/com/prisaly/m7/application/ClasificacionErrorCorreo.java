package com.prisaly.m7.application;

import com.prisaly.m7.domain.ClaseErrorCorreo;
import java.util.Locale;
import java.util.Objects;

/** Maps provider failures to permanent / transient / configuration (D5). Pure. */
public final class ClasificacionErrorCorreo {

    private ClasificacionErrorCorreo() {}

    public static ClaseErrorCorreo clasificar(String codigo, String detalle) {
        Objects.requireNonNull(codigo, "codigo");
        String texto = ((detalle == null ? "" : detalle) + " " + codigo).toLowerCase(Locale.ROOT);
        if (contiene(texto, "authentication", "auth failed", "535", "530", "relay", "sender address rejected", "mailbox unavailable for relay")) {
            return ClaseErrorCorreo.CONFIGURACION;
        }
        if (contiene(texto, "invalid address", "invalid addresses", "user unknown", "mailbox unavailable", "550", "551", "553", "recipient rejected")) {
            return ClaseErrorCorreo.PERMANENTE_DESTINATARIO;
        }
        if (contiene(texto, "421", "450", "451", "452", "timeout", "connection", "temporarily", "try again")) {
            return ClaseErrorCorreo.TRANSITORIO;
        }
        if (codigo.startsWith("5")) {
            return ClaseErrorCorreo.CONFIGURACION;
        }
        if (codigo.startsWith("4")) {
            return ClaseErrorCorreo.TRANSITORIO;
        }
        return ClaseErrorCorreo.TRANSITORIO;
    }

    private static boolean contiene(String texto, String... fragmentos) {
        for (String fragmento : fragmentos) {
            if (texto.contains(fragmento)) {
                return true;
            }
        }
        return false;
    }
}
