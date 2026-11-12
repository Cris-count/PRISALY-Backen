package com.prisaly.m1.application.port;

import java.time.Duration;
import java.util.Optional;

/**
 * D-B2.2 / D-B2.3 abuse controls for student access. Keys are the normalized email STRING (whether or not it belongs
 * to an account, so a limit never reveals account existence) and the TCP peer origin (never a client IP header).
 * Every method returns the remaining wait when a limit is exceeded.
 */
public interface LimiteAccesoEstudiantePort {

    /** Registers a code request; empty when allowed, otherwise the {@code Retry-After}. */
    Optional<Duration> registrarSolicitud(String correoNormalizado, String origen);

    /** Wait imposed on validations from {@code origen} after too many failures; empty when allowed. */
    Optional<Duration> bloqueoValidacion(String origen);

    void registrarValidacionFallida(String origen);
}
