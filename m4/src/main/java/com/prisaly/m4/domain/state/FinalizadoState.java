package com.prisaly.m4.domain.state;

import com.prisaly.m4.domain.model.*;

/**
 * Patrón State, estado Finalizado. No sobrescribe ninguna operación, así que
 * abrir, iniciar, cerrar, editar y convocar quedan rechazados. No hay transición de salida.
 */
public final class FinalizadoState extends AbstractSesionState {
    /** Instancia única del estado terminal. */
    public static final FinalizadoState INSTANCE = new FinalizadoState();

    public EstadoSesion getEstado() {
        return EstadoSesion.FINALIZADO;
    }
}
