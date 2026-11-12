package com.prisaly.m1.domain.student;

import java.security.SecureRandom;
import java.time.Duration;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * D-B2.1 (DC-DD02-B2-02, approved technical baseline): individual access code of 8 digits from {@link SecureRandom},
 * valid 5 minutes, at most 5 validation attempts, single use, at most one open code per student, and a minimum of
 * 60 seconds between requests.
 */
public final class PoliticaCodigoAcceso {

    public static final int DIGITOS = 8;
    public static final Duration VIGENCIA = Duration.ofMinutes(5);
    public static final int INTENTOS_MAXIMOS = 5;
    public static final Duration REENVIO_MINIMO = Duration.ofSeconds(60);
    private static final Pattern FORMATO = Pattern.compile("\\d{" + DIGITOS + "}");
    private static final SecureRandom ALEATORIO = new SecureRandom();

    private PoliticaCodigoAcceso() {
    }

    /** Uniform over 00000000-99999999. */
    public static String generar() {
        return String.format("%0" + DIGITOS + "d", ALEATORIO.nextInt(100_000_000));
    }

    public static boolean formatoValido(String codigo) {
        return codigo != null && FORMATO.matcher(codigo).matches();
    }

    /** Message signed with K-COD: binds the code to its opaque challenge so a code is useless with another one. */
    public static String mensaje(UUID challengeId, String codigo) {
        return "codigo:" + challengeId + ":" + codigo;
    }

    /** Message signed with K-SES for a session token. */
    public static String mensajeSesion(String token) {
        return "sesion:" + token;
    }
}
