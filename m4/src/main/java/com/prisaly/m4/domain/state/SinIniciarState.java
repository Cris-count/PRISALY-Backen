package com.prisaly.m4.domain.state;

import com.prisaly.m4.domain.model.*;

/** Asignación previa, independiente de la autorización académica de acceso. */
public final class SinIniciarState extends AbstractSesionState {
    public static final SinIniciarState INSTANCE = new SinIniciarState();

    public EstadoSesion getEstado() {
        return EstadoSesion.SIN_INICIAR;
    }

    public void iniciar(SesionAcademica s) {
        s.cambiarEstado(EnCursoState.INSTANCE);
    }

    public boolean permiteModificacion() {
        return true;
    }

    public boolean permiteConvocatoria() {
        return true;
    }
}
