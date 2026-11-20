package com.prisaly.m5.application;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import com.prisaly.m5.application.port.RepositorioIntentos;
import com.prisaly.m5.domain.intento.Intento;

/**
 * Doble de prueba de {@link RepositorioIntentos}. Solo existe en las pruebas
 * unitarias: no es evidencia de persistencia PostgreSQL ni de transacciones.
 *
 * <p>Cuenta las llamadas a {@code guardar} para comprobar que una operación
 * rechazada no solicita persistencia.
 */
public final class RepositorioIntentosEnMemoria implements RepositorioIntentos {

    private final Map<ReferenciaIntento, Intento> intentos = new HashMap<>();
    private int guardados;

    /** Prepara un intento existente sin contarlo como guardado por el caso de uso. */
    public Intento existente(ReferenciaIntento referencia, Intento intento) {
        intentos.put(referencia, intento);
        return intento;
    }

    @Override
    public Optional<Intento> buscar(ReferenciaIntento referencia) {
        return Optional.ofNullable(intentos.get(referencia));
    }

    @Override
    public void guardar(ReferenciaIntento referencia, Intento intento) {
        intentos.put(referencia, intento);
        guardados++;
    }

    /** V1-c: reloj y límite simulados del doble (por defecto, límite lejano: el intento no está vencido). */
    private java.time.Instant ahora = java.time.Instant.parse("2026-11-17T10:00:00Z");
    private java.time.Instant limite = java.time.Instant.parse("2026-11-17T11:00:00Z");
    private java.time.Instant ultimoInstante;
    private int lecturasDeReloj;

    /** Fija el instante del "servidor" y el límite persistido que verá el caso de uso. */
    public void reloj(java.time.Instant ahora, java.time.Instant limite) {
        this.ahora = ahora;
        this.limite = limite;
    }

    @Override
    public InstanteDeCierre instanteDeCierre(ReferenciaIntento referencia) {
        lecturasDeReloj++;
        return new InstanteDeCierre(ahora, limite);
    }

    @Override
    public void guardar(ReferenciaIntento referencia, Intento intento, java.time.Instant instante) {
        ultimoInstante = instante;
        guardar(referencia, intento);
    }

    /** Instante con el que se guardó el último cierre (null si ninguno). */
    public java.time.Instant ultimoInstante() {
        return ultimoInstante;
    }

    public int lecturasDeReloj() {
        return lecturasDeReloj;
    }

    public int guardados() {
        return guardados;
    }
}
