package com.prisaly.m4.domain.state;

import com.prisaly.m4.domain.model.EstadoSesion;

import java.util.Objects;

/**
 * Patrón Factory. A partir del valor persistido (SinIniciar, EnCurso, Finalizado)
 * devuelve el objeto State correspondiente. No crea uno nuevo por sesión:
 * reutiliza las instancias INSTANCE. El constructor privado impide instanciar la fábrica.
 */
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
