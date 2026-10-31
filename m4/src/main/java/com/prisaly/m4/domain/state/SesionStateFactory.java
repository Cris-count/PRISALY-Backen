package com.prisaly.m4.domain.state;

import com.prisaly.m4.domain.model.EstadoSesion;

import java.util.Objects;

public final class SesionStateFactory {
    private SesionStateFactory() {}

    public static SesionState of(EstadoSesion e) {
        return switch (Objects.requireNonNull(e)) {
            case SIN_INICIAR -> SinIniciarState.INSTANCE;
            case EN_CURSO -> EnCursoState.INSTANCE;
            case FINALIZADO -> FinalizadoState.INSTANCE;
        };
    }
}
