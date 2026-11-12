package com.prisaly.m1.infrastructure.security;

import com.prisaly.m1.application.port.LimiteAccesoEstudiantePort;
import com.prisaly.m1.domain.student.PoliticaCodigoAcceso;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * D-B2.2 / D-B2.3 (DC-DD02-B2-02): sliding-window limits kept in memory. ACCEPTED ONLY for the single-instance
 * academic environment: counters are lost on restart and are not shared between instances (restriction of D-B2.3).
 * Limits are configurable; the per-origin value must be approved for the institutional shared-IP environment before
 * activation (condition of D-B2.2). Keys are bounded to avoid memory exhaustion.
 */
@Component
@ConditionalOnProperty(name = "prisaly.m1.student-access.enabled", havingValue = "true")
public class LimiteAccesoEstudianteEnMemoria implements LimiteAccesoEstudiantePort {

    static final int CLAVES_MAXIMAS = 50_000;

    private final int porCorreo;
    private final int porOrigen;
    private final int fallosPorOrigen;
    private final Duration ventana;
    private final Clock reloj;
    private final Map<String, Deque<Instant>> solicitudesCorreo = new ConcurrentHashMap<>();
    private final Map<String, Deque<Instant>> solicitudesOrigen = new ConcurrentHashMap<>();
    private final Map<String, Deque<Instant>> fallosOrigen = new ConcurrentHashMap<>();

    @Autowired
    public LimiteAccesoEstudianteEnMemoria(
            @Value("${prisaly.m1.student-access.limites.solicitudes-por-correo:5}") int porCorreo,
            @Value("${prisaly.m1.student-access.limites.solicitudes-por-origen:20}") int porOrigen,
            @Value("${prisaly.m1.student-access.limites.validaciones-fallidas-por-origen:20}") int fallosPorOrigen,
            @Value("${prisaly.m1.student-access.limites.ventana:PT15M}") Duration ventana) {
        this(porCorreo, porOrigen, fallosPorOrigen, ventana, Clock.systemUTC());
    }

    public LimiteAccesoEstudianteEnMemoria(int porCorreo, int porOrigen, int fallosPorOrigen, Duration ventana,
            Clock reloj) {
        if (porCorreo < 1 || porOrigen < 1 || fallosPorOrigen < 1 || ventana == null || ventana.isNegative()
                || ventana.isZero()) {
            throw new IllegalArgumentException("Límites de acceso estudiantil inválidos");
        }
        this.porCorreo = porCorreo;
        this.porOrigen = porOrigen;
        this.fallosPorOrigen = fallosPorOrigen;
        this.ventana = ventana;
        this.reloj = reloj;
    }

    @Override
    public synchronized Optional<Duration> registrarSolicitud(String correoNormalizado, String origen) {
        Instant ahora = reloj.instant();
        Deque<Instant> correo = serie(solicitudesCorreo, correoNormalizado, ahora);
        Deque<Instant> desdeOrigen = serie(solicitudesOrigen, origen, ahora);
        // D-B2.1: minimum 60 s between requests for the same email string (existing or not).
        if (!correo.isEmpty()) {
            Duration desdeUltima = Duration.between(correo.peekLast(), ahora);
            if (desdeUltima.compareTo(PoliticaCodigoAcceso.REENVIO_MINIMO) < 0) {
                return Optional.of(PoliticaCodigoAcceso.REENVIO_MINIMO.minus(desdeUltima));
            }
        }
        Optional<Duration> espera = excedido(correo, porCorreo, ahora).or(() -> excedido(desdeOrigen, porOrigen, ahora));
        if (espera.isPresent()) {
            return espera;
        }
        correo.addLast(ahora);
        desdeOrigen.addLast(ahora);
        return Optional.empty();
    }

    @Override
    public synchronized Optional<Duration> bloqueoValidacion(String origen) {
        Instant ahora = reloj.instant();
        return excedido(serie(fallosOrigen, origen, ahora), fallosPorOrigen, ahora);
    }

    @Override
    public synchronized void registrarValidacionFallida(String origen) {
        Instant ahora = reloj.instant();
        serie(fallosOrigen, origen, ahora).addLast(ahora);
    }

    private Optional<Duration> excedido(Deque<Instant> serie, int maximo, Instant ahora) {
        if (serie.size() < maximo) {
            return Optional.empty();
        }
        Duration espera = Duration.between(ahora, serie.peekFirst().plus(ventana));
        return Optional.of(espera.isNegative() || espera.isZero() ? Duration.ofSeconds(1) : espera);
    }

    private Deque<Instant> serie(Map<String, Deque<Instant>> mapa, String clave, Instant ahora) {
        String k = clave == null ? "" : clave;
        if (mapa.size() >= CLAVES_MAXIMAS && !mapa.containsKey(k)) {
            mapa.entrySet().removeIf(e -> {
                depurar(e.getValue(), ahora);
                return e.getValue().isEmpty();
            });
            if (mapa.size() >= CLAVES_MAXIMAS) {
                // Fail closed for new keys under memory pressure: treat as saturated.
                Deque<Instant> llena = new ArrayDeque<>();
                for (int i = 0; i < Math.max(porCorreo, Math.max(porOrigen, fallosPorOrigen)); i++) {
                    llena.addLast(ahora);
                }
                return llena;
            }
        }
        Deque<Instant> serie = mapa.computeIfAbsent(k, x -> new ArrayDeque<>());
        depurar(serie, ahora);
        return serie;
    }

    private void depurar(Deque<Instant> serie, Instant ahora) {
        Instant limite = ahora.minus(ventana);
        while (!serie.isEmpty() && !serie.peekFirst().isAfter(limite)) {
            serie.removeFirst();
        }
    }
}
