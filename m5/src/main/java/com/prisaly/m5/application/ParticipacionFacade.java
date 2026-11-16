package com.prisaly.m5.application;

import com.prisaly.m5.application.fase.EntradaCuestionario;
import com.prisaly.m5.application.fase.EstadoFase;
import com.prisaly.m5.application.fase.FasesIntento;
import com.prisaly.m5.application.fase.FasesNoDisponiblesException;
import com.prisaly.m5.application.fase.NarrativaIntento;
import com.prisaly.m5.application.servicio.ActualizarRespuestaServicio;
import com.prisaly.m5.application.servicio.ConsultarIntentoServicio;
import com.prisaly.m5.application.servicio.FinalizarIntentoServicio;
import com.prisaly.m5.application.servicio.IniciarIntentoServicio;
import com.prisaly.m5.application.servicio.RegistrarRespuestaServicio;
import com.prisaly.m5.domain.intento.MotivoFinalizacion;
import com.prisaly.m5.domain.respuesta.ReferenciaPregunta;
import com.prisaly.m5.domain.respuesta.Respuesta;
import com.prisaly.m5.domain.respuesta.ValorRespuesta;

/**
 * Fachada (patrón Facade) de los casos de uso de M5 sobre intentos.
 *
 * <p>Es el punto de entrada previsto para los futuros controladores REST:
 * delega cada operación en su servicio especializado y no contiene reglas
 * académicas propias. Devuelve siempre {@link ResumenIntento}, nunca el
 * agregado mutable.
 *
 * <p>Solo expone operaciones implementadas. Iniciar y reanudar comparten
 * operación ({@link #iniciarOReanudar}); el primer inicio sigue bloqueado por
 * las decisiones académicas pendientes, y la repetición (DD-14) no está
 * habilitada.
 */
public final class ParticipacionFacade implements OperacionesParticipacion {

    private final ConsultarIntentoServicio consulta;
    private final RegistrarRespuestaServicio registro;
    private final ActualizarRespuestaServicio actualizacion;
    private final FinalizarIntentoServicio finalizacion;
    private final IniciarIntentoServicio inicio;
    /** DC-BE-SIM-B1-B2A-24: vacío (el valor de producción) deja M5 exactamente como antes del Incremento B. */
    private final java.util.Optional<FasesIntento> fases;

    public ParticipacionFacade(
            ConsultarIntentoServicio consulta,
            RegistrarRespuestaServicio registro,
            ActualizarRespuestaServicio actualizacion,
            FinalizarIntentoServicio finalizacion,
            IniciarIntentoServicio inicio) {
        this(consulta, registro, actualizacion, finalizacion, inicio, java.util.Optional.empty());
    }

    /**
     * Con fases académicas candidatas (Incremento B). Con {@code fases} presente, el inicio crea la fase en la misma
     * transacción y las respuestas se rechazan en exploración (R2) solo en los intentos con fases.
     */
    public ParticipacionFacade(
            ConsultarIntentoServicio consulta,
            RegistrarRespuestaServicio registro,
            ActualizarRespuestaServicio actualizacion,
            FinalizarIntentoServicio finalizacion,
            IniciarIntentoServicio inicio,
            java.util.Optional<FasesIntento> fases) {
        if (consulta == null || registro == null || actualizacion == null || finalizacion == null || inicio == null
                || fases == null) {
            throw new DatoAplicacionInvalidoException("La fachada requiere todos sus servicios");
        }
        this.fases = fases;
        this.inicio = inicio;
        this.consulta = consulta;
        this.registro = registro;
        this.actualizacion = actualizacion;
        this.finalizacion = finalizacion;
    }

    @Override
    public ResultadoInicio iniciarOReanudar(ReferenciaConvocatoria convocatoria) {
        ResultadoInicio resultado = inicio.iniciarOReanudar(convocatoria);
        fases.ifPresent(f -> f.alIniciar(convocatoria, resultado));
        return resultado;
    }

    @Override
    public ResumenIntento consultarIntento(ReferenciaIntento intento) {
        return consulta.consultar(intento);
    }

    @Override
    public ResumenIntento registrarRespuesta(ReferenciaIntento intento, ReferenciaPregunta pregunta, ValorRespuesta valor) {
        fases.ifPresent(f -> f.exigirRespuestasAdmitidas(intento));
        return registro.registrar(intento, pregunta, valor);
    }

    /** Operación provisional, pendiente de ratificación académica. */
    @Override
    public ResumenIntento actualizarRespuesta(ReferenciaIntento intento, ReferenciaPregunta pregunta, ValorRespuesta valor) {
        fases.ifPresent(f -> f.exigirRespuestasAdmitidas(intento));
        return actualizacion.actualizar(intento, pregunta, valor);
    }

    @Override
    public Respuesta guardarRespuesta(ReferenciaIntento intento, ReferenciaPregunta pregunta, ValorRespuesta valor, int revisionEsperada) {
        fases.ifPresent(f -> f.exigirRespuestasAdmitidas(intento));
        return registro.guardar(intento, pregunta, valor, revisionEsperada);
    }

    @Override
    public ResumenIntento finalizarIntento(ReferenciaIntento intento, MotivoFinalizacion motivo) {
        return finalizacion.finalizar(intento, motivo);
    }

    @Override
    public EstadoFase consultarFase(ReferenciaIntento intento) {
        return fases.orElseThrow(FasesNoDisponiblesException::new).consultar(intento);
    }

    @Override
    public EntradaCuestionario iniciarCuestionario(ReferenciaIntento intento) {
        return fases.orElseThrow(FasesNoDisponiblesException::new).iniciarCuestionario(intento);
    }

    @Override
    public NarrativaIntento consultarNarrativa(ReferenciaIntento intento) {
        return fases.orElseThrow(FasesNoDisponiblesException::new).narrativa(intento);
    }
}
