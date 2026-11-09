package com.prisaly.m3.presentation;

import java.util.UUID;

/** Solo acepta la representación canónica completa de un UUID. */
final class IdentificadorCanonico {
    private IdentificadorCanonico() {
    }

    static UUID uuid(String value) {
        UUID id = UUID.fromString(value);
        if (!id.toString().equalsIgnoreCase(value)) {
            throw new IllegalArgumentException("Invalid UUID");
        }
        return id;
    }
}
