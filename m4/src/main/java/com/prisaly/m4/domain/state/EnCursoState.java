package com.prisaly.m4.domain.state;

import com.prisaly.m4.domain.model.*;

public final class EnCursoState extends AbstractSesionState {
    public static final EnCursoState INSTANCE = new EnCursoState();

    public EstadoSesion getEstado() {
        return EstadoSesion.EN_CURSO;
    }

    public void cerrar(SesionAcademica s) {
        s.cambiarEstado(FinalizadoState.INSTANCE);
    }
}
