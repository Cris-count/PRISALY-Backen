package com.prisaly.m5.domain.intento;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import com.prisaly.m5.domain.respuesta.DatoRespuestaInvalidoException;
import com.prisaly.m5.domain.respuesta.ReferenciaPregunta;
import com.prisaly.m5.domain.respuesta.Respuesta;
import com.prisaly.m5.domain.respuesta.ValorRespuesta;

/**
 * Intento de un estudiante sobre un caso situacional.
 *
 * <p>Modela el ciclo de vida aprobado: un intento nace en EnCurso y puede
 * pasar una única vez a Finalizado, dejando registrado el motivo. Finalizado
 * es terminal: no hay regreso a EnCurso ni cambios posteriores.
 *
 * <p>Mientras está EnCurso admite como máximo una respuesta por pregunta,
 * que provisionalmente puede reemplazarse. Toda operación rechazada deja
 * intactos el estado, el motivo y las respuestas. Los argumentos se validan
 * antes que el estado.
 *
 * <p>Deliberadamente no incluye identificador, participación, sesión ni
 * marcas de tiempo: esos atributos y relaciones dependen de BD-005.2 y de los
 * contratos de M1, M3 y M4, todavía no disponibles en el repositorio. Decidir
 * si el tiempo venció corresponde a quien invoca
 * {@link #finalizar(MotivoFinalizacion)}, no a esta clase.
 */
public final class Intento {

    private EstadoIntento estado;
    private MotivoFinalizacion motivoFinalizacion;
    private final Map<ReferenciaPregunta, Respuesta> respuestas = new LinkedHashMap<>();

    private Intento() {
        this.estado = EstadoIntento.EN_CURSO;
    }

    /** Crea un intento nuevo en estado EnCurso y sin respuestas. */
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
     * Registra la primera respuesta del estudiante a una pregunta.
     *
     * @return la respuesta registrada
     * @throws DatoRespuestaInvalidoException si la pregunta o el valor son nulos
     * @throws IntentoFinalizadoException si el intento ya está Finalizado
     * @throws RespuestaDuplicadaException si la pregunta ya tiene respuesta
     */
    public Respuesta registrarRespuesta(ReferenciaPregunta pregunta, ValorRespuesta valor) {
        Respuesta respuesta = new Respuesta(pregunta, valor);
        verificarModificable("registrar una respuesta");
        if (respuestas.containsKey(pregunta)) {
            throw new RespuestaDuplicadaException(pregunta);
        }
        respuestas.put(pregunta, respuesta);
        return respuesta;
    }

    /**
     * Reemplaza la respuesta ya registrada para una pregunta.
     *
     * <p><strong>Decisión provisional de diseño, pendiente de ratificación
     * académica:</strong> se permite cambiar una respuesta mientras el intento
     * siga EnCurso. Si se decide lo contrario, basta con retirar esta
     * operación; el registro inicial no depende de ella. La respuesta
     * reemplazada conserva su posición en el orden de consulta.
     *
     * @return la nueva respuesta
     * @throws DatoRespuestaInvalidoException si la pregunta o el valor son nulos
     * @throws IntentoFinalizadoException si el intento ya está Finalizado
     * @throws RespuestaNoRegistradaException si la pregunta no tiene respuesta
     */
    public Respuesta actualizarRespuesta(ReferenciaPregunta pregunta, ValorRespuesta valor) {
        Respuesta respuesta = new Respuesta(pregunta, valor);
        verificarModificable("actualizar una respuesta");
        if (!respuestas.containsKey(pregunta)) {
            throw new RespuestaNoRegistradaException(pregunta);
        }
        respuestas.put(pregunta, respuesta);
        return respuesta;
    }

    /** Respuestas registradas, en el orden en que se respondió cada pregunta. */
    public List<Respuesta> respuestas() {
        return List.copyOf(respuestas.values());
    }

    /**
     * Respuesta registrada para una pregunta, si existe.
     *
     * @throws DatoRespuestaInvalidoException si {@code pregunta} es nula
     */
    public Optional<Respuesta> respuestaA(ReferenciaPregunta pregunta) {
        if (pregunta == null) {
            throw new DatoRespuestaInvalidoException("La pregunta consultada es obligatoria");
        }
        return Optional.ofNullable(respuestas.get(pregunta));
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

    private void verificarModificable(String operacion) {
        if (!estado.admiteModificaciones()) {
            throw new IntentoFinalizadoException(operacion);
        }
    }

    private void transicionarA(EstadoIntento destino) {
        if (!estado.puedeTransicionarA(destino)) {
            throw new TransicionIntentoInvalidaException(estado, destino);
        }
        this.estado = destino;
    }
}
