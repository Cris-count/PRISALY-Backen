package com.prisaly.m1.application.port;

import java.util.UUID;

/** Emisión y revocación del token de acceso del personal. */
public interface EmisorTokenAccesoPort {
    String issue(UUID userId);

    void revoke(String token);
}
