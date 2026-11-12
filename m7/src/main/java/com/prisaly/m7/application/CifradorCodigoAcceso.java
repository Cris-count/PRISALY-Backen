package com.prisaly.m7.application;

import java.util.Optional;

/**
 * D-B2.7 / D-B5 (DC-DD02-B4-B5-01): encryption of the access-code delivery payload (recipient + code) with K-OUT,
 * AES-256-GCM, a unique 96-bit nonce per message and the key version. The challenge id is authenticated (AAD), so a
 * payload cannot be moved to another row.
 */
public interface CifradorCodigoAcceso {

    Cifrado cifrar(String challengeId, String destinatario, String codigo);

    /** Empty when the key version is unknown/retired or the payload was tampered with (never throws key details). */
    Optional<Contenido> descifrar(String challengeId, byte[] carga, byte[] nonce, String kid);

    record Cifrado(byte[] carga, byte[] nonce, String kid) {
    }

    record Contenido(String destinatario, String codigo) {
        @Override
        public String toString() {
            return "Contenido[redactado]";
        }
    }
}
