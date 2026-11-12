package com.prisaly.m1.application.support;

import com.prisaly.m1.domain.student.ClavesAccesoEstudiante;
import com.prisaly.m1.domain.student.ConjuntoClavesHmac;
import java.security.SecureRandom;
import java.util.Base64;

/** Disposable random test keys (never a production secret): a fresh value on every call. */
public final class ClavesDePrueba {
    private static final SecureRandom ALEATORIO = new SecureRandom();

    private ClavesDePrueba() {
    }

    /** {@code kid:base64} with {@code bytes} random bytes. */
    public static String especificacion(String kid, int bytes) {
        byte[] valor = new byte[bytes];
        ALEATORIO.nextBytes(valor);
        return kid + ":" + Base64.getEncoder().encodeToString(valor);
    }

    public static ClavesAccesoEstudiante claves() {
        return new ClavesAccesoEstudiante(ConjuntoClavesHmac.leer("K-COD", especificacion("cod-qa", 32)),
                ConjuntoClavesHmac.leer("K-SES", especificacion("ses-qa", 32)));
    }
}
