package com.prisaly.m4.domain.state;

import com.prisaly.m4.domain.model.EstadoSesion;
import com.prisaly.m4.domain.model.SesionAcademica;

/**
 * Patrón State. La sesión no decide con ifs qué puede hacer: delega en el objeto
 * de estado que tenga montado (SinIniciar, EnCurso o Finalizado).
 *
 * <p>Cada estado concreto permite solo sus transiciones y rechaza el resto.
 * Abrir, iniciar, cerrar y cancelar cambian de estado; el resto de operaciones
 * se niega desde {@link AbstractSesionState}.</p>
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
