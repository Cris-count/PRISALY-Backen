package com.prisaly.m1.application.port;

/** DM1-08: verifiable blocklist of common or compromised passwords. */
public interface ContrasenasBloqueadasPort {
    /** @param normalizada the candidate already lower-cased */
    boolean contiene(String normalizada);

    /** SHA-256 of the list source, so the list in force can be verified. */
    String huellaSha256();

    int tamano();
}
