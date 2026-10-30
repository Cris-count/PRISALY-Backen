package com.prisaly.m5.domain.intento;

import java.util.Optional;

/**
 * Intento de un estudiante sobre un caso situacional.
 *
 * <p>Modela solo el ciclo de vida aprobado: un intento nace en EnCurso y
 * puede pasar una única vez a Finalizado, dejando registrado el motivo.
 * Finalizado es terminal: no hay regreso a EnCurso ni cambios posteriores.
 *
 * <p>Deliberadamente no incluye identificador, participación, sesión,
 * respuestas ni marcas de tiempo: esos atributos y relaciones dependen de
 * BD-005.2 y de los contratos de M1, M3 y M4, todavía no disponibles en el
 * repositorio. Decidir si el tiempo venció corresponde a quien invoca
 * {@link #finalizar(MotivoFinalizacion)}, no a esta clase.
 */
public final class Intento {

    private EstadoIntento estado;
    private MotivoFinalizacion motivoFinalizacion;

    private Intento() {
        this.estado = EstadoIntento.EN_CURSO;
    }

    /** Crea un intento nuevo en estado EnCurso. */
    public static Intento iniciar() {
        return new Intento();
    }

    public EstadoIntento estado() {
        return estado;
    }

    /** Motivo de finalización; vacío mientras el intento está en curso. */
    public Optional<MotivoFinalizacion> motivoFinalizacion() {
        return Optional.ofNullable(motivoFinalizacion);
    }

    public boolean estaEnCurso() {
        return estado == EstadoIntento.EN_CURSO;
    }

    public boolean estaFinalizado() {
        return estado == EstadoIntento.FINALIZADO;
    }

    /**
     * Finaliza el intento registrando el motivo.
     *
     * <p>Una segunda finalización se rechaza siempre, con el mismo motivo o con
     * otro, y no altera el estado ni el motivo ya registrados.
     *
     * @throws MotivoFinalizacionRequeridoException si {@code motivo} es nulo
     * @throws TransicionIntentoInvalidaException si el intento ya está Finalizado
     */
    public void finalizar(MotivoFinalizacion motivo) {
        if (motivo == null) {
            throw new MotivoFinalizacionRequeridoException();
        }
        transicionarA(EstadoIntento.FINALIZADO);
        this.motivoFinalizacion = motivo;
    }

    private void transicionarA(EstadoIntento destino) {
        if (!estado.puedeTransicionarA(destino)) {
            throw new TransicionIntentoInvalidaException(estado, destino);
        }
        this.estado = destino;
    }
}
