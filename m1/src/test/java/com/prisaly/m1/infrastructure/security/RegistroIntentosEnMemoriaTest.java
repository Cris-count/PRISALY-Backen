package com.prisaly.m1.infrastructure.security;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.prisaly.m1.application.port.RegistroIntentosAutenticacionPort.Reserva;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;

/** DM1-06 (DC-M1-04): 5 failures per account, 20 per origin, 15-minute sliding window. */
class RegistroIntentosEnMemoriaTest {
    private static final Duration VENTANA = Duration.ofMinutes(15);

    private static final class Reloj extends Clock {
        private Instant ahora = Instant.parse("2026-11-10T10:00:00Z");
        void avanzar(Duration d) { ahora = ahora.plus(d); }
        @Override public ZoneId getZone() { return ZoneOffset.UTC; }
        @Override public Clock withZone(ZoneId zone) { return this; }
        @Override public Instant instant() { return ahora; }
    }

    private final Reloj reloj = new Reloj();
    private final RegistroIntentosEnMemoria registro = new RegistroIntentosEnMemoria(reloj, 5, 20, VENTANA);

    private void fallar(String cuenta, String origen, int veces) {
        for (int i = 0; i < veces; i++) {
            Reserva r = registro.reservar(cuenta, origen);
            assertFalse(r.bloqueada(), "intento " + (i + 1) + " no debía bloquearse");
            registro.confirmarFallo(r);
        }
    }

    @Test
    void elSextoIntentoDeLaCuentaSeLimitaConEsperaHastaQueSaleElMasAntiguo() {
        fallar("cuenta", "10.0.0.1", 5);
        reloj.avanzar(Duration.ofMinutes(1));
        Reserva sexto = registro.reservar("cuenta", "10.0.0.1");
        assertTrue(sexto.bloqueada());
        assertEquals(Duration.ofMinutes(14), sexto.bloqueo().orElseThrow());
    }

    @Test
    void laVentanaEsDeslizanteYElContadorSeRecupera() {
        fallar("cuenta", "10.0.0.1", 5);
        reloj.avanzar(VENTANA.minusSeconds(1));
        assertTrue(registro.reservar("cuenta", "10.0.0.1").bloqueada());
        reloj.avanzar(Duration.ofSeconds(1));
        Reserva recuperada = registro.reservar("cuenta", "10.0.0.1");
        assertFalse(recuperada.bloqueada());
    }

    @Test
    void unExitoReiniciaElContadorDeLaCuentaYNoCuentaContraElOrigen() {
        fallar("cuenta", "10.0.0.1", 4);
        Reserva exito = registro.reservar("cuenta", "10.0.0.1");
        registro.confirmarExito(exito);
        fallar("cuenta", "10.0.0.1", 5); // the counter started again from zero
        assertTrue(registro.reservar("cuenta", "10.0.0.1").bloqueada());
        // origin: 4 + 5 failures = 9 (the success did not count), still below 20
        assertFalse(registro.reservar("otra", "10.0.0.1").bloqueada());
    }

    @Test
    void elLimitePorOrigenDetieneAtaquesContraMuchasCuentas() {
        for (int i = 0; i < 20; i++) {
            fallar("cuenta-" + i, "10.0.0.9", 1);
        }
        Reserva vigesimoPrimero = registro.reservar("cuenta-nueva", "10.0.0.9");
        assertTrue(vigesimoPrimero.bloqueada());
        assertFalse(registro.reservar("cuenta-nueva", "10.0.0.10").bloqueada()); // other origins unaffected
    }

    @Test
    void ataquesDistribuidosDesdeVariosOrigenesSeLimitanPorCuenta() {
        for (int i = 0; i < 5; i++) {
            fallar("victima", "10.0.1." + i, 1);
        }
        assertTrue(registro.reservar("victima", "10.0.1.99").bloqueada());
        assertFalse(registro.reservar("otra-cuenta", "10.0.1.99").bloqueada());
    }

    @Test
    void laEsperaEsLaMayorEntreCuentaYOrigen() {
        fallar("a", "10.0.0.4", 5);                    // account limited from t0: free at t0+15
        reloj.avanzar(Duration.ofMinutes(5));
        for (int i = 0; i < 20; i++) {
            fallar("b" + i, "10.0.0.5", 1);           // origin 10.0.0.5 limited from t0+5: free at t0+20
        }
        assertEquals(Duration.ofMinutes(15), registro.reservar("a", "10.0.0.5").bloqueo().orElseThrow());
        assertEquals(Duration.ofMinutes(10), registro.reservar("a", "10.0.0.4").bloqueo().orElseThrow());
    }

    @Test
    void liberarNoCuentaComoFallo() {
        for (int i = 0; i < 10; i++) {
            registro.liberar(registro.reservar("cuenta", "10.0.0.1"));
        }
        assertFalse(registro.reservar("cuenta", "10.0.0.1").bloqueada());
    }

    @Test
    void intentosConcurrentesNuncaSuperanElUmbral() throws Exception {
        ExecutorService hilos = Executors.newFixedThreadPool(16);
        CountDownLatch salida = new CountDownLatch(1);
        List<Future<Boolean>> resultados = new ArrayList<>();
        for (int i = 0; i < 64; i++) {
            resultados.add(hilos.submit(() -> {
                salida.await();
                Reserva r = registro.reservar("concurrente", "10.0.0.7");
                return !r.bloqueada();
            }));
        }
        salida.countDown();
        int admitidos = 0;
        for (Future<Boolean> f : resultados) {
            admitidos += f.get(10, TimeUnit.SECONDS) ? 1 : 0;
        }
        hilos.shutdownNow();
        assertEquals(5, admitidos);
    }

    @Test
    void parametrosNoPositivosSeRechazan() {
        assertThrows(IllegalArgumentException.class, () -> new RegistroIntentosEnMemoria(reloj, 0, 20, VENTANA));
        assertThrows(IllegalArgumentException.class, () -> new RegistroIntentosEnMemoria(reloj, 5, 0, VENTANA));
        assertThrows(IllegalArgumentException.class, () -> new RegistroIntentosEnMemoria(reloj, 5, 20, Duration.ZERO));
    }
}
