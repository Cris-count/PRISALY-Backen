package com.prisaly.m4.domain.state;

import com.prisaly.m4.domain.model.EstadoSesion;
import com.prisaly.m4.domain.model.SesionAcademica;

/**
 * Estado EN_CURSO: los estudiantes están participando.
 *
 * <p>Transiciones permitidas: EN_CURSO → CERRADA, EN_CURSO → CANCELADA.</p>
 * <p>No se permiten modificaciones ni nuevas convocatorias.</p>
 */
public class EnCursoState extends AbstractSesionState {

    public static final EnCursoState INSTANCE = new EnCursoState();

    @Override
    public EstadoSesion getEstado() {
        return EstadoSesion.EN_CURSO;
    }

    @Override
    public void cerrar(SesionAcademica sesion) {
        sesion.cambiarEstado(CerradaState.INSTANCE);
    }

    @Override
    public void cancelar(SesionAcademica sesion) {
        sesion.cambiarEstado(CanceladaState.INSTANCE);
    }
}
