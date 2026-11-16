package com.prisaly.m5.application;

import com.prisaly.m5.application.fase.CompletitudIntento;
import com.prisaly.m5.application.fase.EntradaCuestionario;
import com.prisaly.m5.application.fase.EstadoFase;
import com.prisaly.m5.application.fase.FasesNoDisponiblesException;
import com.prisaly.m5.application.fase.NarrativaIntento;
import com.prisaly.m5.domain.intento.MotivoFinalizacion;
import com.prisaly.m5.domain.respuesta.ReferenciaPregunta;
import com.prisaly.m5.domain.respuesta.Respuesta;
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

    /** Lectura de contenido por el proxy protegido; las composiciones sin proveedor deniegan. */
    default ContenidoIntento consultarContenido(ReferenciaIntento intento) {
        throw new ContenidoIntentoNoDisponibleException();
    }

    ResumenIntento registrarRespuesta(ReferenciaIntento intento, ReferenciaPregunta pregunta, ValorRespuesta valor);

    /** Operación provisional, pendiente de ratificación académica. */
    ResumenIntento actualizarRespuesta(ReferenciaIntento intento, ReferenciaPregunta pregunta, ValorRespuesta valor);

    default Respuesta guardarRespuesta(ReferenciaIntento intento, ReferenciaPregunta pregunta, ValorRespuesta valor, int revisionEsperada) {
        throw new UnsupportedOperationException("CAS requiere el servicio M5");
    }

    ResumenIntento finalizarIntento(ReferenciaIntento intento, MotivoFinalizacion motivo);

    // DC-BE-SIM-B1-B2A-24 (Incremento B, B2a): operaciones internas candidatas, sin ruta publicada. Las composiciones
    // sin fases habilitadas fallan cerradas, como consultarContenido.

    /** Fase persistida del intento (P2). */
    default EstadoFase consultarFase(ReferenciaIntento intento) {
        throw new FasesNoDisponiblesException();
    }

    /** Entrada explícita e idempotente al cuestionario (P3). */
    default EntradaCuestionario iniciarCuestionario(ReferenciaIntento intento) {
        throw new FasesNoDisponiblesException();
    }

    /** Guion publicado que corresponde al intento (P1). */
    default NarrativaIntento consultarNarrativa(ReferenciaIntento intento) {
        throw new FasesNoDisponiblesException();
    }

    /** Completitud informativa (P4), en la misma instantánea que el contenido autorizado. */
    default CompletitudIntento consultarCompletitud(ReferenciaIntento intento) {
        throw new ContenidoIntentoNoDisponibleException();
    }
}
