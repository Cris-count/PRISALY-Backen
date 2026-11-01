package com.prisaly.m4.domain.state;

import com.prisaly.m4.domain.model.*;

public final class FinalizadoState extends AbstractSesionState {
    public static final FinalizadoState INSTANCE = new FinalizadoState();

    public EstadoSesion getEstado() {
        return EstadoSesion.FINALIZADO;
    }
}
