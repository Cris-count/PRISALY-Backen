package com.prisaly.m4.domain.state;

import com.prisaly.m4.domain.model.EstadoSesion;
import com.prisaly.m4.domain.model.SesionAcademica;
import com.prisaly.m4.domain.rule.SesionOperacionNoPermitidaException;

/**
 * Patrón State, parte común. Niega abrir, iniciar, cerrar, cancelar, editar y convocar.
 * Cada estado concreto sobrescribe solo lo que sí puede hacer. Así un estado nuevo
 * no hereda un permiso por accidente.
 */
abstract class AbstractSesionState implements SesionState {

    protected void rechazar(String operacion) {
        throw new SesionOperacionNoPermitidaException(getEstado().name(), operacion);
    }

    @Override
    public void abrir(SesionAcademica sesion) {
        rechazar("abrir");
    }

    @Override
    public void iniciar(SesionAcademica sesion) {
        rechazar("iniciar");
    }

    @Override
    public void cerrar(SesionAcademica sesion) {
        rechazar("cerrar");
    }

    @Override
    public void cancelar(SesionAcademica sesion) {
        rechazar("cancelar");
    }

    @Override
    public boolean permiteModificacion() {
        return false;
    }

    @Override
    public boolean permiteConvocatoria() {
        return false;
    }
}
