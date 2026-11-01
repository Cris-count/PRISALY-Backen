package com.prisaly.m1.application.port;

/**
 * Shared student identity contract for M4/M5.
 * Separate from {@link IdentityAccessPort} (personnel). Do not resolve students from personal tokens.
 */
public interface StudentAccessPort {

    /**
     * Student bound to the current authenticated request.
     * Until DD-02 is ratified, the production adapter denies all calls.
     */
    AuthenticatedStudent requireCurrentStudent();
}
