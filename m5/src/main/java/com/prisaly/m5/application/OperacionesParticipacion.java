package com.prisaly.m5.application;

import com.prisaly.m5.domain.intento.MotivoFinalizacion;
import com.prisaly.m5.domain.respuesta.ReferenciaPregunta;
import com.prisaly.m5.domain.respuesta.ValorRespuesta;

/**
 * Operaciones de M5 sobre un intento existente. La implementan la fachada de
 * aplicación, su decorador transaccional y el proxy de protección, de modo
 * que pueden componerse sin que la aplicación dependa de la infraestructura.
 */
public interface OperacionesParticipacion {

    ResumenIntento consultarIntento(ReferenciaIntento intento);

    ResumenIntento registrarRespuesta(ReferenciaIntento intento, ReferenciaPregunta pregunta, ValorRespuesta valor);

    /** Operación provisional, pendiente de ratificación académica. */
    ResumenIntento actualizarRespuesta(ReferenciaIntento intento, ReferenciaPregunta pregunta, ValorRespuesta valor);

    ResumenIntento finalizarIntento(ReferenciaIntento intento, MotivoFinalizacion motivo);
}
