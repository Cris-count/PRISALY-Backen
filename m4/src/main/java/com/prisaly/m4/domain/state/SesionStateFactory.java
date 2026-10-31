package com.prisaly.m4.domain.state;

import com.prisaly.m4.domain.model.EstadoSesion;

/**
 * Factoría que devuelve la instancia {@link SesionState} correspondiente
 * a un {@link EstadoSesion} persistido.
 */
public final class SesionStateFactory {

    private SesionStateFactory() {
    }

    public static SesionState of(EstadoSesion estado) {
        return switch (estado) {
            case PROGRAMADA -> ProgramadaState.INSTANCE;
            case ABIERTA    -> AbiertaState.INSTANCE;
            case EN_CURSO   -> EnCursoState.INSTANCE;
            case CERRADA    -> CerradaState.INSTANCE;
            case CANCELADA  -> CanceladaState.INSTANCE;
        };
    }
}
