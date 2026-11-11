package com.prisaly.m1.domain.rule;

import com.prisaly.m1.api.IdentityDomainException;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.function.Predicate;

/**
 * DM1-08 (DC-M1-04): password policy applied before any credential is stored or replaced.
 * No composition rules; length counted in Unicode code points; BCrypt only reads 72 UTF-8 bytes.
 */
public final class PoliticaContrasena {
    public static final int MINIMO_CARACTERES = 12;
    public static final int MAXIMO_BYTES_UTF8 = 72;
    public static final String CODE_TOO_SHORT = "PASSWORD_TOO_SHORT";
    public static final String CODE_TOO_LONG = "PASSWORD_TOO_LONG";
    public static final String CODE_COMMON = "PASSWORD_COMMON";
    public static final String CODE_CONTAINS_EMAIL = "PASSWORD_CONTAINS_EMAIL";

    private PoliticaContrasena() {
    }

    /**
     * @param bloqueada blocklist lookup over the lower-cased candidate
     * @param correo the account email; the password must not contain it (case-insensitive)
     */
    public static void validar(String contrasena, String correo, Predicate<String> bloqueada) {
        if (contrasena == null || contrasena.codePointCount(0, contrasena.length()) < MINIMO_CARACTERES) {
            throw new IdentityDomainException(CODE_TOO_SHORT, "Password must have at least 12 characters");
        }
        if (contrasena.getBytes(StandardCharsets.UTF_8).length > MAXIMO_BYTES_UTF8) {
            throw new IdentityDomainException(CODE_TOO_LONG, "Password must not exceed 72 UTF-8 bytes");
        }
        String normalizada = contrasena.toLowerCase(Locale.ROOT);
        if (bloqueada.test(normalizada)) {
            throw new IdentityDomainException(CODE_COMMON, "Password is too common");
        }
        if (correo != null && !correo.isBlank() && normalizada.contains(correo.trim().toLowerCase(Locale.ROOT))) {
            throw new IdentityDomainException(CODE_CONTAINS_EMAIL, "Password must not contain the account email");
        }
    }
}
