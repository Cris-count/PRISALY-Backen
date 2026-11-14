package com.prisaly.m4.domain.state;

import com.prisaly.m4.domain.model.*;

/**
 * Patrón State, estado EnCurso. La única transición que sobrescribe es cerrar(),
 * que monta {@link FinalizadoState}. Editar y convocar siguen negados por la clase base:
 * la convocatoria se hace antes, en SinIniciar.
 */
public final class EnCursoState extends AbstractSesionState {
    /** Instancia única. No guarda la sesión; la sesión guarda la referencia a este estado. */
    public static final EnCursoState INSTANCE = new EnCursoState();

    public EstadoSesion getEstado() {
        return EstadoSesion.EN_CURSO;
    }

    /** Transición permitida: EnCurso hacia Finalizado. */
    public void cerrar(SesionAcademica s) {
        s.cambiarEstado(FinalizadoState.INSTANCE);
    }
}
