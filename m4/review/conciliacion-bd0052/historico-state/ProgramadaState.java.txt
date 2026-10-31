package com.prisaly.m4.domain.state;

import com.prisaly.m4.domain.model.EstadoSesion;
import com.prisaly.m4.domain.model.SesionAcademica;

/**
 * Estado PROGRAMADA: la sesión acaba de crearse.
 *
 * <p>Transiciones permitidas: PROGRAMADA → ABIERTA, PROGRAMADA → CANCELADA.</p>
 * <p>Se permite modificar datos de programación y cancelar.</p>
 */
public class ProgramadaState extends AbstractSesionState {

    public static final ProgramadaState INSTANCE = new ProgramadaState();

    @Override
    public EstadoSesion getEstado() {
        return EstadoSesion.PROGRAMADA;
    }

    @Override
    public void abrir(SesionAcademica sesion) {
        sesion.cambiarEstado(AbiertaState.INSTANCE);
    }

    @Override
    public void cancelar(SesionAcademica sesion) {
        sesion.cambiarEstado(CanceladaState.INSTANCE);
    }

    @Override
    public boolean permiteModificacion() {
        return true;
    }
}
