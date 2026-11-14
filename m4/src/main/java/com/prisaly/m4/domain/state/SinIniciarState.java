package com.prisaly.m4.domain.state;

import com.prisaly.m4.domain.model.*;

/**
 * Patrón State, estado SinIniciar. Es el único que deja editar la sesión y convocar.
 * iniciar() pasa el contexto a {@link EnCursoState}. No se comparte estado mutable:
 * INSTANCE es la única instancia, porque este objeto no guarda datos de una sesión.
 */
public final class SinIniciarState extends AbstractSesionState {
    /** Instancia única de este estado. Todas las sesiones SinIniciar apuntan aquí. */
    public static final SinIniciarState INSTANCE = new SinIniciarState();

    public EstadoSesion getEstado() {
        return EstadoSesion.SIN_INICIAR;
    }

    /** Transición permitida: SinIniciar hacia EnCurso. No convoca ni cierra por sí misma. */
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
