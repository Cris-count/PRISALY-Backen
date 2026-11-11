package com.prisaly.m1.application.port;

import java.util.UUID;

/** Emisión y revocación del token de acceso del personal. */
public interface EmisorTokenAccesoPort {
    String issue(UUID userId);

    void revoke(String token);

    /** The issuer cannot hold more active tokens. Declared here so presentation does not depend on infrastructure (A17). */
    class CapacidadAgotadaException extends RuntimeException {
        public CapacidadAgotadaException(String message) {
            super(message);
        }
    }
}
