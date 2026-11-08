package com.prisaly.m1.domain.student;

import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.util.Arrays;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

/** HMAC-SHA256. El código y el token de sesión no se guardan en claro. */
public final class FirmasEstudiante {

    private FirmasEstudiante() {
    }

    public static byte[] hmac(byte[] clave, String mensaje) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(clave, "HmacSHA256"));
            return mac.doFinal(mensaje.getBytes(StandardCharsets.UTF_8));
        } catch (GeneralSecurityException ex) {
            throw new IllegalStateException("HMAC no disponible", ex);
        }
    }

    public static boolean iguales(byte[] izquierda, byte[] derecha) {
        if (izquierda == null || derecha == null) {
            return false;
        }
        return MessageDigest.isEqual(izquierda, derecha);
    }

    public static byte[] copiar(byte[] valor) {
        return Arrays.copyOf(valor, valor.length);
    }
}
