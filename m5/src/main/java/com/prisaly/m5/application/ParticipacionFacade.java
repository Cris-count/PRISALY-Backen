package com.prisaly.m5.application;

import com.prisaly.m5.application.servicio.ActualizarRespuestaServicio;
import com.prisaly.m5.application.servicio.ConsultarIntentoServicio;
import com.prisaly.m5.application.servicio.FinalizarIntentoServicio;
import com.prisaly.m5.application.servicio.IniciarIntentoServicio;
import com.prisaly.m5.application.servicio.RegistrarRespuestaServicio;
import com.prisaly.m5.domain.intento.MotivoFinalizacion;
import com.prisaly.m5.domain.respuesta.ReferenciaPregunta;
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

    public ParticipacionFacade(
            ConsultarIntentoServicio consulta,
            RegistrarRespuestaServicio registro,
            ActualizarRespuestaServicio actualizacion,
            FinalizarIntentoServicio finalizacion,
            IniciarIntentoServicio inicio) {
        if (consulta == null || registro == null || actualizacion == null || finalizacion == null || inicio == null) {
            throw new DatoAplicacionInvalidoException("La fachada requiere todos sus servicios");
        }
        this.inicio = inicio;
        this.consulta = consulta;
        this.registro = registro;
        this.actualizacion = actualizacion;
        this.finalizacion = finalizacion;
    }

    @Override
    public ResultadoInicio iniciarOReanudar(ReferenciaConvocatoria convocatoria) {
        return inicio.iniciarOReanudar(convocatoria);
    }

    @Override
    public ResumenIntento consultarIntento(ReferenciaIntento intento) {
        return consulta.consultar(intento);
    }

    @Override
    public ResumenIntento registrarRespuesta(ReferenciaIntento intento, ReferenciaPregunta pregunta, ValorRespuesta valor) {
        return registro.registrar(intento, pregunta, valor);
    }

    /** Operación provisional, pendiente de ratificación académica. */
    @Override
    public ResumenIntento actualizarRespuesta(ReferenciaIntento intento, ReferenciaPregunta pregunta, ValorRespuesta valor) {
        return actualizacion.actualizar(intento, pregunta, valor);
    }

    @Override
    public ResumenIntento finalizarIntento(ReferenciaIntento intento, MotivoFinalizacion motivo) {
        return finalizacion.finalizar(intento, motivo);
    }
}
