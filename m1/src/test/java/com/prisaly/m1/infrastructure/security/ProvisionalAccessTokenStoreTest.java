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

    @Test
    void limpiaVencidosAbandonadosSinInvalidarTokensVigentes() {
        TestClock clock = new TestClock();
        var store = new ProvisionalAccessTokenStore(clock, 60, 2);
        store.issue(USER);
        clock.advance(Duration.ofSeconds(30));
        String vigente = store.issue(USER);
        clock.advance(Duration.ofSeconds(30));
        assertEquals(1, store.purgeExpired());
        assertEquals(Optional.of(USER), store.resolveActiveUserId(vigente));
        assertEquals(0, store.purgeExpired());
        store.issue(USER);
        assertThrows(ProvisionalAccessTokenStore.CapacityExceededException.class, () -> store.issue(USER));
        store.revoke(vigente);
        store.issue(USER);
    }

    /**
     * DM1-07 (DC-M1-03) characterization, not a desired rule: the store has no per-user limit, so a single
     * account can exhaust the global capacity and other users get CapacityExceeded (HTTP 503) until expiry.
     * This documents the risk reported to Dirección Central; it must change when a per-user limit is approved.
     */
    @Test
    void caracterizacionDm107_unSoloUsuarioAgotaLaCapacidadGlobal() {
        TestClock clock = new TestClock();
        var store = new ProvisionalAccessTokenStore(clock, 60, 3);
        for (int i = 0; i < 3; i++) {
            store.issue(USER);
        }
        UUID otro = UUID.fromString("22222222-2222-2222-2222-222222222222");
        assertThrows(ProvisionalAccessTokenStore.CapacityExceededException.class, () -> store.issue(otro));
        clock.advance(Duration.ofSeconds(60));
        assertTrue(store.resolveActiveUserId(store.issue(otro)).isPresent());
    }

    @Test
    void emitirReclamaCapacidadDeVencidos() {
        TestClock clock = new TestClock();
        var store = new ProvisionalAccessTokenStore(clock, 1, 1);
        String anterior = store.issue(USER);
        clock.advance(Duration.ofSeconds(1));
        String actual = store.issue(USER);
        assertTrue(store.resolveActiveUserId(anterior).isEmpty());
        assertEquals(Optional.of(USER), store.resolveActiveUserId(actual));
        assertThrows(IllegalArgumentException.class, () -> new ProvisionalAccessTokenStore(clock, 1, 0));
    }

    @Test
    void emisionesConcurrentesRespetanCapacidadSinRevocarSesiones() throws Exception {
        var store = new ProvisionalAccessTokenStore(new TestClock(), 60, 8);
        var accepted = new java.util.concurrent.ConcurrentLinkedQueue<String>();
        var rejected = new java.util.concurrent.atomic.AtomicInteger();
        try (var pool = java.util.concurrent.Executors.newFixedThreadPool(8)) {
            var tasks = new java.util.ArrayList<java.util.concurrent.Callable<Void>>();
            for (int i = 0; i < 40; i++) tasks.add(() -> {
                try { accepted.add(store.issue(USER)); }
                catch (ProvisionalAccessTokenStore.CapacityExceededException full) { rejected.incrementAndGet(); }
                return null;
            });
            for (var result : pool.invokeAll(tasks)) result.get();
        }
        assertEquals(8, accepted.size());
        assertEquals(32, rejected.get());
        accepted.forEach(token -> assertEquals(Optional.of(USER), store.resolveActiveUserId(token)));
        assertEquals(8, new java.util.HashSet<>(accepted).size());
    }
}
