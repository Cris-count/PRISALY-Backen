package com.prisaly.m7.infrastructure;

import com.prisaly.m7.application.CifradorCodigoAcceso;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;
import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;

/**
 * K-OUT (D-B5, DC-DD02-B4-B5-01): AES-256-GCM with a fresh 96-bit nonce from {@link SecureRandom} per message, a
 * 128-bit tag and the challenge id as associated data. Key specification {@code kid:base64[,kid:base64...]}: the first
 * version encrypts, the others only decrypt during rotation. Each key is exactly 32 random bytes (AES-256).
 * Key material is never logged nor exposed by {@code toString}.
 */
public final class CifradorCodigoAccesoAesGcm implements CifradorCodigoAcceso {

    static final int BYTES_CLAVE = 32;
    static final int BYTES_NONCE = 12;
    private static final int BITS_TAG = 128;
    private static final Pattern KID = Pattern.compile("[A-Za-z0-9._-]{1,40}");
    private static final String SEPARADOR = "\n";

    private final Map<String, byte[]> claves;
    private final String kidActual;
    private final SecureRandom aleatorio = new SecureRandom();

    private CifradorCodigoAccesoAesGcm(Map<String, byte[]> claves, String kidActual) {
        this.claves = claves;
        this.kidActual = kidActual;
    }

    /** @throws IllegalArgumentException missing, malformed, not 32 bytes or repeated key (message without key material) */
    public static CifradorCodigoAccesoAesGcm leer(String especificacion) {
        if (especificacion == null || especificacion.isBlank()) {
            throw new IllegalArgumentException("Falta la clave K-OUT");
        }
        Map<String, byte[]> claves = new LinkedHashMap<>();
        for (String parte : especificacion.split(",")) {
            String entrada = parte.strip();
            int separador = entrada.indexOf(':');
            if (separador <= 0 || !KID.matcher(entrada.substring(0, separador)).matches()) {
                throw new IllegalArgumentException("Formato inválido en la clave K-OUT (se espera kid:base64)");
            }
            byte[] valor;
            try {
                valor = Base64.getDecoder().decode(entrada.substring(separador + 1));
            } catch (IllegalArgumentException ex) {
                throw new IllegalArgumentException("La clave K-OUT no es Base64 válido");
            }
            if (valor.length != BYTES_CLAVE) {
                throw new IllegalArgumentException("La clave K-OUT debe tener exactamente 32 bytes (AES-256)");
            }
            for (byte[] previa : claves.values()) {
                if (MessageDigest.isEqual(previa, valor)) {
                    throw new IllegalArgumentException("Dos versiones de la clave K-OUT son iguales");
                }
            }
            if (claves.putIfAbsent(entrada.substring(0, separador), valor) != null) {
                throw new IllegalArgumentException("Versión repetida en la clave K-OUT");
            }
        }
        return new CifradorCodigoAccesoAesGcm(claves, claves.keySet().iterator().next());
    }

    @Override
    public Cifrado cifrar(String challengeId, String destinatario, String codigo) {
        if (destinatario == null || destinatario.contains(SEPARADOR) || codigo == null || codigo.contains(SEPARADOR)) {
            throw new IllegalArgumentException("Contenido de entrega inválido");
        }
        byte[] nonce = new byte[BYTES_NONCE];
        aleatorio.nextBytes(nonce);
        try {
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(claves.get(kidActual), "AES"),
                    new GCMParameterSpec(BITS_TAG, nonce));
            cipher.updateAAD(challengeId.getBytes(StandardCharsets.UTF_8));
            byte[] carga = cipher.doFinal(("v1" + SEPARADOR + destinatario + SEPARADOR + codigo)
                    .getBytes(StandardCharsets.UTF_8));
            return new Cifrado(carga, nonce, kidActual);
        } catch (GeneralSecurityException ex) {
            throw new IllegalStateException("Cifrado AES-GCM no disponible");
        }
    }

    @Override
    public Optional<Contenido> descifrar(String challengeId, byte[] carga, byte[] nonce, String kid) {
        byte[] clave = kid == null ? null : claves.get(kid);
        if (clave == null || carga == null || nonce == null || nonce.length != BYTES_NONCE) {
            return Optional.empty();
        }
        try {
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, new SecretKeySpec(clave, "AES"), new GCMParameterSpec(BITS_TAG, nonce));
            cipher.updateAAD(challengeId.getBytes(StandardCharsets.UTF_8));
            String[] partes = new String(cipher.doFinal(carga), StandardCharsets.UTF_8).split(SEPARADOR, -1);
            if (partes.length != 3 || !"v1".equals(partes[0])) {
                return Optional.empty();
            }
            return Optional.of(new Contenido(partes[1], partes[2]));
        } catch (GeneralSecurityException | IllegalArgumentException ex) {
            return Optional.empty();
        }
    }

    public String kidActual() {
        return kidActual;
    }

    /** Copies for the APP-level independence check (K-OUT must not equal K-COD or K-SES); never log or persist. */
    public List<byte[]> materialParaComparar() {
        List<byte[]> copia = new ArrayList<>();
        for (byte[] v : claves.values()) {
            copia.add(Arrays.copyOf(v, v.length));
        }
        return copia;
    }

    /** Distinct key ids, for diagnostics without material. */
    public Set<String> kids() {
        return new HashSet<>(claves.keySet());
    }

    @Override
    public String toString() {
        return "CifradorCodigoAccesoAesGcm[kids=" + claves.keySet() + "]";
    }
}
