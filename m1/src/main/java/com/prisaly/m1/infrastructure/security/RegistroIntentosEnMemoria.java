package com.prisaly.m1.infrastructure.security;

import com.prisaly.m1.application.port.RegistroIntentosAutenticacionPort;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * DM1-06 (DC-M1-04): sliding-window failure counters kept IN MEMORY. Academic local environment only: counters are
 * lost on restart and are not shared between instances. Not a production solution. Never locks or modifies accounts.
 */
@Component
public class RegistroIntentosEnMemoria implements RegistroIntentosAutenticacionPort {
    private final Clock clock;
    private final int maxPorCuenta;
    private final int maxPorOrigen;
    private final Duration ventana;
    private final Map<String, Deque<Entrada>> porCuenta = new HashMap<>();
    private final Map<String, Deque<Entrada>> porOrigen = new HashMap<>();
    private final AtomicLong secuencia = new AtomicLong();

    @Autowired
    public RegistroIntentosEnMemoria(
            @Value("${prisaly.m1.autenticacion.intentos.max-por-cuenta:5}") int maxPorCuenta,
            @Value("${prisaly.m1.autenticacion.intentos.max-por-origen:20}") int maxPorOrigen,
            @Value("${prisaly.m1.autenticacion.intentos.ventana:PT15M}") Duration ventana) {
        this(Clock.systemUTC(), maxPorCuenta, maxPorOrigen, ventana);
    }

    public RegistroIntentosEnMemoria(Clock clock, int maxPorCuenta, int maxPorOrigen, Duration ventana) {
        if (maxPorCuenta < 1 || maxPorOrigen < 1 || ventana == null || ventana.isNegative() || ventana.isZero()) {
            throw new IllegalArgumentException("Authentication attempt limits must be positive");
        }
        this.clock = Objects.requireNonNull(clock, "clock");
        this.maxPorCuenta = maxPorCuenta;
        this.maxPorOrigen = maxPorOrigen;
        this.ventana = ventana;
    }

    private record Entrada(long id, Instant instante) { }

    @Override
    public synchronized Reserva reservar(String claveCuenta, String origen) {
        Instant ahora = clock.instant();
        Deque<Entrada> cuenta = vigentes(porCuenta, claveCuenta, ahora);
        Deque<Entrada> desdeOrigen = vigentes(porOrigen, origen, ahora);
        Optional<Duration> espera = espera(cuenta, maxPorCuenta, ahora)
                .map(a -> espera(desdeOrigen, maxPorOrigen, ahora).filter(b -> b.compareTo(a) > 0).orElse(a))
                .or(() -> espera(desdeOrigen, maxPorOrigen, ahora));
        if (espera.isPresent()) {
            return new Reserva(claveCuenta, origen, -1, espera);
        }
        long id = secuencia.incrementAndGet();
        cuenta.addLast(new Entrada(id, ahora));
        desdeOrigen.addLast(new Entrada(id, ahora));
        return new Reserva(claveCuenta, origen, id, Optional.empty());
    }

    @Override
    public synchronized void confirmarExito(Reserva reserva) {
        if (reserva.bloqueada()) {
            return;
        }
        porCuenta.remove(reserva.claveCuenta()); // a success resets the account counter
        quitar(porOrigen, reserva.origen(), reserva.id()); // and does not count against the origin
    }

    @Override
    public synchronized void confirmarFallo(Reserva reserva) {
        // Already counted at reservation time.
    }

    @Override
    public synchronized void liberar(Reserva reserva) {
        if (reserva.bloqueada()) {
            return;
        }
        quitar(porCuenta, reserva.claveCuenta(), reserva.id());
        quitar(porOrigen, reserva.origen(), reserva.id());
    }

    private Deque<Entrada> vigentes(Map<String, Deque<Entrada>> mapa, String clave, Instant ahora) {
        Deque<Entrada> entradas = mapa.computeIfAbsent(clave, k -> new ArrayDeque<>());
        Instant limite = ahora.minus(ventana);
        while (!entradas.isEmpty() && !entradas.peekFirst().instante().isAfter(limite)) {
            entradas.pollFirst();
        }
        return entradas;
    }

    /** Time until enough failures leave the window to allow one more attempt. */
    private Optional<Duration> espera(Deque<Entrada> entradas, int maximo, Instant ahora) {
        if (entradas.size() < maximo) {
            return Optional.empty();
        }
        Entrada liberadora = entradas.stream().skip(entradas.size() - maximo).findFirst().orElseThrow();
        Duration restante = Duration.between(ahora, liberadora.instante().plus(ventana));
        return Optional.of(restante.isNegative() || restante.isZero() ? Duration.ofSeconds(1) : restante);
    }

    private static void quitar(Map<String, Deque<Entrada>> mapa, String clave, long id) {
        Deque<Entrada> entradas = mapa.get(clave);
        if (entradas != null) {
            entradas.removeIf(e -> e.id() == id);
            if (entradas.isEmpty()) {
                mapa.remove(clave);
            }
        }
    }
}
