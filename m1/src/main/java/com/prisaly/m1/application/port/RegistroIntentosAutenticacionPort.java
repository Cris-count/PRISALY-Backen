package com.prisaly.m1.application.port;

import java.time.Duration;
import java.util.Optional;

/**
 * DM1-06 (DC-M1-04): counters of failed personnel authentications per account and per origin.
 * An attempt is reserved (counted as a failure) atomically BEFORE the password is checked, so concurrent attempts can
 * never exceed the thresholds; a success releases its reservation and resets the account counter.
 */
public interface RegistroIntentosAutenticacionPort {

    /** @return the reservation, or empty with {@link #esperaRestante} &gt; 0 when the account or origin is limited */
    Reserva reservar(String claveCuenta, String origen);

    void confirmarExito(Reserva reserva);

    /** Counted already at reservation time; kept for readability and future persistence. */
    void confirmarFallo(Reserva reserva);

    /** The attempt failed for a reason unrelated to the credential (e.g. capacity): it does not count. */
    void liberar(Reserva reserva);

    record Reserva(String claveCuenta, String origen, long id, Optional<Duration> bloqueo) {
        public boolean bloqueada() {
            return bloqueo.isPresent();
        }
    }
}
