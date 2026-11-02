package com.prisaly.m5.application;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import com.prisaly.m5.application.port.RepositorioParticipaciones;
import com.prisaly.m5.domain.intento.EstadoIntento;
import com.prisaly.m5.domain.intento.Intento;

/**
 * Doble de prueba de {@link RepositorioParticipaciones}. Solo pruebas
 * unitarias: no es evidencia de bloqueos, unicidad ni transacciones de
 * PostgreSQL (eso lo prueba {@code InicioIntentoIT}).
 *
 * <p>Los intentos que crea se registran también en
 * {@link RepositorioIntentosEnMemoria}, para que el caso de uso pueda leerlos.
 */
public final class RepositorioParticipacionesEnMemoria implements RepositorioParticipaciones {

    /** Intento registrado con sus tiempos y su número. */
    public record FilaIntento(ReferenciaIntento referencia, int numero, Instant inicio, Instant limiteTemporal) {
    }

    private final RepositorioIntentosEnMemoria intentos;
    private final Map<ReferenciaConvocatoria, ReferenciaParticipacion> participaciones = new HashMap<>();
    private final Map<ReferenciaParticipacion, List<FilaIntento>> intentosPorParticipacion = new HashMap<>();
    private final List<String> escrituras = new ArrayList<>();
    private Instant reloj = Instant.parse("2026-11-01T15:00:00Z");
    private int secuencia;

    /** Acción ejecutada al crear la participación: simula otra solicitud que se adelantó. */
    private Runnable alCrear = () -> { };

    public RepositorioParticipacionesEnMemoria(RepositorioIntentosEnMemoria intentos) {
        this.intentos = intentos;
    }

    /** Prepara una participación existente, sin contarla como escritura. */
    public ReferenciaParticipacion participacionExistente(ReferenciaConvocatoria convocatoria) {
        return participaciones.computeIfAbsent(convocatoria, c -> new ReferenciaParticipacion("P-" + c.valor()));
    }

    /** Prepara un intento existente de la participación, sin contarlo como escritura. */
    public FilaIntento intentoExistente(ReferenciaConvocatoria convocatoria, Intento intento, Instant inicio, Instant limite) {
        ReferenciaParticipacion participacion = participacionExistente(convocatoria);
        List<FilaIntento> filas = intentosPorParticipacion.computeIfAbsent(participacion, p -> new ArrayList<>());
        FilaIntento fila = new FilaIntento(new ReferenciaIntento("I-" + (++secuencia)), filas.size() + 1, inicio, limite);
        filas.add(fila);
        intentos.existente(fila.referencia(), intento);
        return fila;
    }

    public void alCrearParticipacion(Runnable accion) {
        this.alCrear = accion;
    }

    public void reloj(Instant ahora) {
        this.reloj = ahora;
    }

    public List<String> escrituras() {
        return escrituras;
    }

    public Optional<ReferenciaParticipacion> participacionDe(ReferenciaConvocatoria convocatoria) {
        return Optional.ofNullable(participaciones.get(convocatoria));
    }

    public List<FilaIntento> intentosDe(ReferenciaConvocatoria convocatoria) {
        ReferenciaParticipacion participacion = participaciones.get(convocatoria);
        return participacion == null ? List.of() : intentosPorParticipacion.getOrDefault(participacion, List.of());
    }

    @Override
    public Optional<ReferenciaParticipacion> bloquear(ReferenciaConvocatoria convocatoria) {
        return Optional.ofNullable(participaciones.get(convocatoria));
    }

    @Override
    public ReferenciaParticipacion crearOBloquear(ReferenciaConvocatoria convocatoria) {
        alCrear.run();
        if (!participaciones.containsKey(convocatoria)) {
            escrituras.add("participacion");
        }
        return participacionExistente(convocatoria);
    }

    @Override
    public Optional<IntentoEnCurso> intentoEnCurso(ReferenciaParticipacion participacion) {
        return intentosPorParticipacion.getOrDefault(participacion, List.of()).stream()
                .filter(f -> intentos.buscar(f.referencia()).map(i -> i.estado() == EstadoIntento.EN_CURSO).orElse(false))
                .map(f -> new IntentoEnCurso(f.referencia(), f.inicio(), f.limiteTemporal()))
                .findFirst();
    }

    @Override
    public boolean tieneIntentos(ReferenciaParticipacion participacion) {
        return !intentosPorParticipacion.getOrDefault(participacion, List.of()).isEmpty();
    }

    @Override
    public Instant ahora() {
        return reloj;
    }

    @Override
    public ReferenciaIntento crearPrimerIntento(ReferenciaParticipacion participacion, Instant inicio, Instant limiteTemporal) {
        List<FilaIntento> filas = intentosPorParticipacion.computeIfAbsent(participacion, p -> new ArrayList<>());
        FilaIntento fila = new FilaIntento(new ReferenciaIntento("I-" + (++secuencia)), 1, inicio, limiteTemporal);
        filas.add(fila);
        intentos.existente(fila.referencia(), Intento.iniciar());
        escrituras.add("intento");
        return fila.referencia();
    }
}
