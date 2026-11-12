package com.prisaly.m1.infrastructure.security;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

/** D-B2.1 (60 s) and D-B2.2 (5 per email, 20 per origin, 20 failed validations per origin, 15-minute window). */
class LimiteAccesoEstudianteEnMemoriaTest {

    private final AtomicReference<Instant> ahora = new AtomicReference<>(Instant.parse("2026-11-11T10:00:00Z"));
    private final Clock reloj = new Clock() {
        @Override public ZoneOffset getZone() { return ZoneOffset.UTC; }
        @Override public Clock withZone(java.time.ZoneId zone) { return this; }
        @Override public Instant instant() { return ahora.get(); }
    };

    private LimiteAccesoEstudianteEnMemoria limites(int correo, int origen, int fallos) {
        return new LimiteAccesoEstudianteEnMemoria(correo, origen, fallos, Duration.ofMinutes(15), reloj);
    }

    private void avanzar(Duration d) {
        ahora.set(ahora.get().plus(d));
    }

    @Test
    void reenvioAntesDe60SegundosSeRechazaConLaEsperaRestante() {
        var l = limites(5, 20, 20);
        assertTrue(l.registrarSolicitud("a@x.test", "10.0.0.1").isEmpty());
        avanzar(Duration.ofSeconds(20));
        assertEquals(Duration.ofSeconds(40), l.registrarSolicitud("a@x.test", "10.0.0.1").orElseThrow());
        avanzar(Duration.ofSeconds(40));
        assertTrue(l.registrarSolicitud("a@x.test", "10.0.0.1").isEmpty());
    }

    @Test
    void cincoSolicitudesPorCorreoEn15MinutosYLaVentanaSeDesliza() {
        var l = limites(5, 100, 20);
        for (int i = 0; i < 5; i++) {
            assertTrue(l.registrarSolicitud("a@x.test", "10.0.0." + i).isEmpty(), "solicitud " + i);
            avanzar(Duration.ofSeconds(61));
        }
        var espera = l.registrarSolicitud("a@x.test", "10.0.0.9").orElseThrow();
        // First request at t0, sixth at t0+305 s: wait until t0+15 min = 595 s.
        assertEquals(Duration.ofSeconds(595), espera);
        avanzar(espera);
        assertTrue(l.registrarSolicitud("a@x.test", "10.0.0.9").isEmpty());
    }

    @Test
    void elLimitePorCorreoSeAplicaIgualAUnCorreoInexistente() {
        // The limiter only sees the email string: a 429 cannot reveal whether the account exists.
        var l = limites(1, 100, 20);
        assertTrue(l.registrarSolicitud("nadie@x.test", "o").isEmpty());
        avanzar(Duration.ofSeconds(61));
        assertTrue(l.registrarSolicitud("nadie@x.test", "o").isPresent());
    }

    @Test
    void veinteSolicitudesPorOrigenIndependienteDelCorreo() {
        var l = limites(5, 20, 20);
        for (int i = 0; i < 20; i++) {
            assertTrue(l.registrarSolicitud("e" + i + "@x.test", "192.0.2.7").isEmpty());
        }
        assertTrue(l.registrarSolicitud("otro@x.test", "192.0.2.7").isPresent());
        assertTrue(l.registrarSolicitud("otro@x.test", "192.0.2.8").isEmpty(), "otro origen no queda afectado");
    }

    @Test
    void veinteValidacionesFallidasPorOrigenBloqueanHastaQueCorreLaVentana() {
        var l = limites(5, 20, 20);
        for (int i = 0; i < 20; i++) {
            assertTrue(l.bloqueoValidacion("o").isEmpty());
            l.registrarValidacionFallida("o");
        }
        assertTrue(l.bloqueoValidacion("o").isPresent());
        avanzar(Duration.ofMinutes(15));
        assertTrue(l.bloqueoValidacion("o").isEmpty());
    }

    @Test
    void limitesConfigurablesInvalidosNoArrancan() {
        assertThrows(IllegalArgumentException.class, () -> limites(0, 20, 20));
        assertThrows(IllegalArgumentException.class,
                () -> new LimiteAccesoEstudianteEnMemoria(5, 20, 20, Duration.ZERO, reloj));
    }
}
