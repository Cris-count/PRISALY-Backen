package com.prisaly.m1.infrastructure.security;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class ProvisionalAccessTokenStoreTest {

    private static final UUID USER = UUID.fromString("11111111-1111-1111-1111-111111111111");

    // Mutable clock for expiry checks.
    private static final class TestClock extends Clock {
        private Instant now = Instant.parse("2026-11-08T10:00:00Z");

        void advance(Duration duration) {
            now = now.plus(duration);
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return now;
        }
    }

    @Test
    void elTokenCaducaAlCumplirseElTtlYNoReviveDespues() {
        TestClock clock = new TestClock();
        ProvisionalAccessTokenStore store = new ProvisionalAccessTokenStore(clock, 60);
        String token = store.issue(USER);

        clock.advance(Duration.ofSeconds(59));
        assertEquals(Optional.of(USER), store.resolveActiveUserId(token));

        clock.advance(Duration.ofSeconds(1));
        assertTrue(store.resolveActiveUserId(token).isEmpty());

        clock.advance(Duration.ofSeconds(-30));
        assertTrue(store.resolveActiveUserId(token).isEmpty(), "an expired token is discarded, not suspended");
    }

    @Test
    void elTokenRevocadoODesconocidoNoResuelveIdentidad() {
        ProvisionalAccessTokenStore store = new ProvisionalAccessTokenStore(new TestClock(), 60);
        String token = store.issue(USER);
        String otro = store.issue(USER);
        assertNotEquals(token, otro);

        store.revoke(token);

        assertTrue(store.resolveActiveUserId(token).isEmpty());
        assertEquals(Optional.of(USER), store.resolveActiveUserId(otro));
        assertTrue(store.resolveActiveUserId("inventado").isEmpty());
    }

    @Test
    void rechazaUnTtlNoPositivo() {
        assertThrows(IllegalArgumentException.class, () -> new ProvisionalAccessTokenStore(new TestClock(), 0));
    }
}
