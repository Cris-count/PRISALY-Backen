package com.prisaly.m4.domain.state;

import com.prisaly.m4.domain.model.EstadoSesion;
import com.prisaly.m4.domain.model.SesionAcademica;
import com.prisaly.m4.domain.rule.SesionOperacionNoPermitidaException;

/**
 * Clase base abstracta con comportamiento por defecto: rechazar toda operación.
 * Las subclases habilitan selectivamente las transiciones permitidas.
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
