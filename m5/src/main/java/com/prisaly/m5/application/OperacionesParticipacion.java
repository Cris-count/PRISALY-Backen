package com.prisaly.m5.application;

import com.prisaly.m5.domain.intento.MotivoFinalizacion;
import com.prisaly.m5.domain.respuesta.ReferenciaPregunta;
import com.prisaly.m5.domain.respuesta.ValorRespuesta;

/**
 * Operaciones de M5 sobre la participación de un estudiante: iniciar o
 * reanudar su intento y operar sobre él. La implementan la fachada de
 * aplicación, su decorador transaccional y el proxy de protección, de modo
 * que pueden componerse sin que la aplicación dependa de la infraestructura.
 */
public interface OperacionesParticipacion {

    /**
     * Devuelve el intento en curso de la convocatoria o, si no hay ninguno,
     * crea la participación y el primer intento. Reanudar no crea otro intento
     * ni modifica sus tiempos. El primer inicio falla de forma segura mientras
     * la elegibilidad o el límite temporal no estén ratificados.
     */
    ResultadoInicio iniciarOReanudar(ReferenciaConvocatoria convocatoria);

    ResumenIntento consultarIntento(ReferenciaIntento intento);

    ResumenIntento registrarRespuesta(ReferenciaIntento intento, ReferenciaPregunta pregunta, ValorRespuesta valor);

    /** Operación provisional, pendiente de ratificación académica. */
    ResumenIntento actualizarRespuesta(ReferenciaIntento intento, ReferenciaPregunta pregunta, ValorRespuesta valor);

    ResumenIntento finalizarIntento(ReferenciaIntento intento, MotivoFinalizacion motivo);
}
