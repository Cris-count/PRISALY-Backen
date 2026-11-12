package com.prisaly.m1.domain.student;

import java.util.ArrayList;
import java.util.Base64;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * D-B5 (DC-DD02-B4-B5-01): versioned HMAC-SHA-256 server key. The specification is {@code kid:base64[,kid:base64...]};
 * the first entry signs, the following ones only verify during an ordinary rotation window.
 * Every key has at least {@link #BYTES_MINIMOS} random bytes (Base64, never a plain text password).
 * Key bytes are never exposed by {@code toString} and never logged.
 */
public final class ConjuntoClavesHmac {

    public static final int BYTES_MINIMOS = 32;
    private static final Pattern KID = Pattern.compile("[A-Za-z0-9._-]{1,40}");

    /** One key version. */
    public record Version(String kid, byte[] valor) {
        public Version {
            valor = FirmasEstudiante.copiar(valor);
        }

        @Override
        public String toString() {
            return "Version[kid=" + kid + "]";
        }
    }

    private final String nombre;
    private final List<Version> versiones;

    private ConjuntoClavesHmac(String nombre, List<Version> versiones) {
        this.nombre = nombre;
        this.versiones = List.copyOf(versiones);
    }

    /**
     * @param nombre logical name used only in error messages (never the key)
     * @throws IllegalArgumentException missing, malformed, short or repeated key; the message never contains key material
     */
    public static ConjuntoClavesHmac leer(String nombre, String especificacion) {
        if (especificacion == null || especificacion.isBlank()) {
            throw new IllegalArgumentException("Falta la clave " + nombre);
        }
        List<Version> versiones = new ArrayList<>();
        Set<String> kids = new HashSet<>();
        for (String parte : especificacion.split(",")) {
            String entrada = parte.strip();
            int separador = entrada.indexOf(':');
            if (separador <= 0) {
                throw new IllegalArgumentException("Formato inválido en la clave " + nombre + " (se espera kid:base64)");
            }
            String kid = entrada.substring(0, separador);
            if (!KID.matcher(kid).matches()) {
                throw new IllegalArgumentException("Identificador de versión inválido en la clave " + nombre);
            }
            byte[] valor;
            try {
                valor = Base64.getDecoder().decode(entrada.substring(separador + 1));
            } catch (IllegalArgumentException ex) {
                throw new IllegalArgumentException("La clave " + nombre + " no es Base64 válido");
            }
            if (valor.length < BYTES_MINIMOS) {
                throw new IllegalArgumentException("La clave " + nombre + " tiene menos de " + BYTES_MINIMOS + " bytes");
            }
            if (!kids.add(kid)) {
                throw new IllegalArgumentException("Versión repetida en la clave " + nombre);
            }
            for (Version previa : versiones) {
                if (FirmasEstudiante.iguales(previa.valor(), valor)) {
                    throw new IllegalArgumentException("Dos versiones de la clave " + nombre + " son iguales");
                }
            }
            versiones.add(new Version(kid, valor));
        }
        return new ConjuntoClavesHmac(nombre, versiones);
    }

    public String kidActual() {
        return versiones.get(0).kid();
    }

    /** Signs with the current version. */
    public byte[] firmar(String mensaje) {
        return FirmasEstudiante.hmac(versiones.get(0).valor(), mensaje);
    }

    /** Signature with a given version (current or previous), for verification during rotation. */
    public Optional<byte[]> firmarCon(String kid, String mensaje) {
        return versiones.stream().filter(v -> v.kid().equals(kid)).findFirst()
                .map(v -> FirmasEstudiante.hmac(v.valor(), mensaje));
    }

    /** Signatures with every active version, current first (session lookup during rotation). */
    public List<byte[]> firmasCandidatas(String mensaje) {
        return versiones.stream().map(v -> FirmasEstudiante.hmac(v.valor(), mensaje)).toList();
    }

    /** True when any version of this set shares key material with any version of {@code otro}. */
    public boolean compartenMaterial(ConjuntoClavesHmac otro) {
        for (Version a : versiones) {
            for (Version b : otro.versiones) {
                if (FirmasEstudiante.iguales(a.valor(), b.valor())) {
                    return true;
                }
            }
        }
        return false;
    }

    /** Raw bytes for cross-module distinctness checks only; callers must not log or persist them. */
    public List<byte[]> materialParaComparar() {
        return versiones.stream().map(v -> FirmasEstudiante.copiar(v.valor())).toList();
    }

    @Override
    public String toString() {
        return "ConjuntoClavesHmac[" + nombre + ", kids=" + versiones.stream().map(Version::kid).toList() + "]";
    }
}
