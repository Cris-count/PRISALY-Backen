package com.prisaly.m4.domain.state;

import com.prisaly.m4.domain.model.EstadoSesion;
import com.prisaly.m4.domain.model.SesionAcademica;

/**
 * Interfaz del patrón GoF State para controlar el comportamiento
 * de una sesión académica según su estado actual.
 *
 * <p>Cada implementación concreta define qué operaciones están
 * permitidas y cuáles son rechazadas en ese estado.</p>
 */
public interface SesionState {

    /** Devuelve el {@link EstadoSesion} que esta instancia representa. */
    EstadoSesion getEstado();

    /**
     * Intenta abrir la sesión para convocatorias.
     *
     * @throws com.prisaly.m4.domain.rule.SesionOperacionNoPermitidaException
     *         si la transición no está permitida desde el estado actual.
     */
    void abrir(SesionAcademica sesion);

    /**
     * Inicia la sesión (pasa a EN_CURSO).
     *
     * @throws com.prisaly.m4.domain.rule.SesionOperacionNoPermitidaException
     *         si la transición no está permitida.
     */
    void iniciar(SesionAcademica sesion);

    /**
     * Cierra la sesión.
     *
     * @throws com.prisaly.m4.domain.rule.SesionOperacionNoPermitidaException
     *         si la transición no está permitida.
     */
    void cerrar(SesionAcademica sesion);

    /**
     * Cancela la sesión.
     *
     * @throws com.prisaly.m4.domain.rule.SesionOperacionNoPermitidaException
     *         si la transición no está permitida.
     */
    void cancelar(SesionAcademica sesion);

    /**
     * Indica si se permite modificar los datos de programación en este estado.
     */
    boolean permiteModificacion();

    /**
     * Indica si se pueden registrar convocatorias en este estado.
     */
    boolean permiteConvocatoria();
}
