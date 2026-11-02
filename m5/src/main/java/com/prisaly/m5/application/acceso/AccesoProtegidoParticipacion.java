package com.prisaly.m5.application.acceso;

import com.prisaly.m5.application.DatoAplicacionInvalidoException;
import com.prisaly.m5.application.OperacionesParticipacion;
import com.prisaly.m5.application.ReferenciaConvocatoria;
import com.prisaly.m5.application.ReferenciaIntento;
import com.prisaly.m5.application.ResultadoInicio;
import com.prisaly.m5.application.ResumenIntento;
import com.prisaly.m5.domain.intento.MotivoFinalizacion;
import com.prisaly.m5.domain.respuesta.ReferenciaPregunta;
import com.prisaly.m5.domain.respuesta.ValorRespuesta;

/**
 * Proxy de protección (patrón Protection Proxy) de las operaciones de M5 para
 * un estudiante: verifica identidad (M1) y titularidad (M4) con
 * {@link ControlAccesoIntentos} antes de delegar, y no delega si la
 * verificación falla.
 *
 * <p>Ninguna operación recibe la identidad del estudiante: la obtiene el
 * servidor. Es el punto de entrada previsto para los futuros controladores de
 * estudiante, que siguen sin existir (ARQ-008.3 y DD-02 pendientes). Con
 * {@code DenyingStudentAccessService} de M1, toda llamada se deniega.
 */
public final class AccesoProtegidoParticipacion implements OperacionesParticipacion {

    private final ControlAccesoIntentos control;
    private final OperacionesParticipacion operaciones;

    public AccesoProtegidoParticipacion(ControlAccesoIntentos control, OperacionesParticipacion operaciones) {
        if (control == null || operaciones == null) {
            throw new DatoAplicacionInvalidoException("El acceso protegido requiere control y operaciones");
        }
        this.control = control;
        this.operaciones = operaciones;
    }

    /**
     * La convocatoria debe ser del estudiante autenticado; el intento que se
     * reanuda pertenece a ella, así que también es suyo.
     */
    @Override
    public ResultadoInicio iniciarOReanudar(ReferenciaConvocatoria convocatoria) {
        control.verificarConvocatoria(convocatoria);
        return operaciones.iniciarOReanudar(convocatoria);
    }

    @Override
    public ResumenIntento consultarIntento(ReferenciaIntento intento) {
        control.verificar(intento);
        return operaciones.consultarIntento(intento);
    }

    @Override
    public ResumenIntento registrarRespuesta(ReferenciaIntento intento, ReferenciaPregunta pregunta, ValorRespuesta valor) {
        control.verificar(intento);
        return operaciones.registrarRespuesta(intento, pregunta, valor);
    }

    /** Operación provisional, pendiente de ratificación académica. */
    @Override
    public ResumenIntento actualizarRespuesta(ReferenciaIntento intento, ReferenciaPregunta pregunta, ValorRespuesta valor) {
        control.verificar(intento);
        return operaciones.actualizarRespuesta(intento, pregunta, valor);
    }

    /**
     * El motivo lo decide el servidor (no hay DTO de finalización): el futuro
     * controlador no debe aceptarlo del cliente.
     */
    @Override
    public ResumenIntento finalizarIntento(ReferenciaIntento intento, MotivoFinalizacion motivo) {
        control.verificar(intento);
        return operaciones.finalizarIntento(intento, motivo);
    }
}
