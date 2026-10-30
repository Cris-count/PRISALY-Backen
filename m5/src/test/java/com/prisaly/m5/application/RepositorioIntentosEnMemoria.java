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

    public int guardados() {
        return guardados;
    }
}
