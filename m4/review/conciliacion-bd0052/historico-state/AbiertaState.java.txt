package com.prisaly.m4.domain.state;

import com.prisaly.m4.domain.model.EstadoSesion;
import com.prisaly.m4.domain.model.SesionAcademica;

/**
 * Estado ABIERTA: se pueden registrar convocatorias.
 *
 * <p>Transiciones permitidas: ABIERTA → EN_CURSO, ABIERTA → CANCELADA.</p>
 */
public class AbiertaState extends AbstractSesionState {

    public static final AbiertaState INSTANCE = new AbiertaState();

    @Override
    public EstadoSesion getEstado() {
        return EstadoSesion.ABIERTA;
    }

    @Override
    public void iniciar(SesionAcademica sesion) {
        sesion.cambiarEstado(EnCursoState.INSTANCE);
    }

    @Override
    public void cancelar(SesionAcademica sesion) {
        sesion.cambiarEstado(CanceladaState.INSTANCE);
    }

    @Override
    public boolean permiteModificacion() {
        return true;
    }

    @Override
    public boolean permiteConvocatoria() {
        return true;
    }
}
