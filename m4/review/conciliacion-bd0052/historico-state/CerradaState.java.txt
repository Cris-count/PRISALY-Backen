package com.prisaly.m4.domain.state;

import com.prisaly.m4.domain.model.EstadoSesion;

/**
 * Estado CERRADA: estado terminal exitoso.
 * No se permiten más transiciones.
 */
public class CerradaState extends AbstractSesionState {

    public static final CerradaState INSTANCE = new CerradaState();

    @Override
    public EstadoSesion getEstado() {
        return EstadoSesion.CERRADA;
    }
}
