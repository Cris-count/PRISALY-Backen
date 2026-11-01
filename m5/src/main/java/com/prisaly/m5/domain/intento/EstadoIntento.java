package com.prisaly.m5.domain.intento;

/**
 * Estados aprobados del ciclo de vida de un intento: EnCurso y Finalizado.
 *
 * <p>El vencimiento del tiempo no es un estado. Un intento cuyo tiempo vence
 * pasa a {@link #FINALIZADO} con el motivo
 * {@link MotivoFinalizacion#TIEMPO_VENCIDO}.
 */
public enum EstadoIntento {

    /** EnCurso: el estudiante está desarrollando el intento. */
    EN_CURSO,

    /** Finalizado: estado terminal; el intento ya no admite cambios. */
    FINALIZADO;

    /**
     * Indica si la transición desde este estado hacia {@code destino} es válida.
     * La única transición permitida es EnCurso → Finalizado.
     */
    public boolean puedeTransicionarA(EstadoIntento destino) {
        return this == EN_CURSO && destino == FINALIZADO;
    }

    /** Indica si un intento en este estado admite modificaciones. */
    public boolean admiteModificaciones() {
        return this == EN_CURSO;
    }
}
