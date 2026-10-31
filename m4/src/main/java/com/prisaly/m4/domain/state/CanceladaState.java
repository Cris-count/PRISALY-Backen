package com.prisaly.m4.domain.state;

import com.prisaly.m4.domain.model.EstadoSesion;

/**
 * Estado CANCELADA: estado terminal alternativo.
 * No se permiten más transiciones.
 */
public class CanceladaState extends AbstractSesionState {

    public static final CanceladaState INSTANCE = new CanceladaState();

    @Override
    public EstadoSesion getEstado() {
        return EstadoSesion.CANCELADA;
    }
}
