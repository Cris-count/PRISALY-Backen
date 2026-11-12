package com.prisaly.m1.application.service;

import java.time.Duration;

/** D-B2.2: a student-access limit was exceeded; mapped to 429 {@code AUTH_RATE_LIMITED} with {@code Retry-After}. */
public class LimiteAccesoEstudianteException extends RuntimeException {

    private final Duration reintentarEn;

    public LimiteAccesoEstudianteException(Duration reintentarEn) {
        super("Too many student access requests");
        this.reintentarEn = reintentarEn;
    }

    /** Whole seconds, at least 1. */
    public long segundos() {
        return Math.max(1, (reintentarEn.toMillis() + 999) / 1000);
    }
}
