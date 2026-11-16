package com.prisaly.m5.application.fase;

import com.prisaly.m5.application.LimiteTemporalVencidoException;
import com.prisaly.m5.application.ReferenciaIntento;
import com.prisaly.m5.domain.intento.EstadoIntento;

/**
 * Resoluciones de Dirección Central para el Incremento B (DC-BE-SIM-B2B-B3-32), una comprobación por decisión. Ninguna
 * escribe, calcula una nota ni cierra un intento por vencimiento. Las condiciones (fase, estado y límite leídos con la
 * fila {@code "Intento"} bloqueada o en la instantánea de lectura) las evalúa {@link FasesIntento} antes de llamar.
 */
public final class PoliticasFaseAprobadas implements PuntosDecisionFase {

    /**
     * D-SIM-2: las preguntas siguen ocultas mientras no se haya entrado al cuestionario, también tras finalizar
     * (409 {@code CUESTIONARIO_NO_INICIADO}, el código aprobado; QA33-01).
     */
    @Override
    public void preguntasSinCuestionario(ReferenciaIntento intento, EstadoIntento estado) {
        throw new CuestionarioNoIniciadoException(MENSAJE_PREGUNTAS);
    }

    static final String MENSAJE_PREGUNTAS = "El cuestionario del intento no ha comenzado: sus preguntas no están disponibles";

    /**
     * D-SIM-3: la entrada al cuestionario se rechaza después del límite temporal (409 {@code LIMITE_TEMPORAL_VENCIDO}).
     * Se llama cuando la escritura atómica no se aplicó porque el límite ya había vencido en su propio instante de
     * PostgreSQL; la fase sigue en exploración.
     */
    @Override
    public void entradaAlCuestionarioTrasElLimite(ReferenciaIntento intento) {
        throw new LimiteTemporalVencidoException(MENSAJE_LIMITE);
    }

    static final String MENSAJE_LIMITE = "El límite temporal del intento venció: ya no se puede entrar al cuestionario";

    /**
     * D-SIM-4: se permite finalizar desde la exploración. La confirmación visible la muestra Angular y no es un control
     * de seguridad: autenticación, titularidad e idempotencia las conservan el Proxy y el servicio de finalización. Sin
     * calificación automática (M5 no califica).
     */
    @Override
    public void finalizacionDesdeExploracion(ReferenciaIntento intento) {
        // Permitido.
    }

    /** D-SIM-10: la narrativa queda cerrada después de finalizar (409 {@code EXPLORACION_CERRADA}); la fase no cambia. */
    @Override
    public void narrativaDeIntentoFinalizado(ReferenciaIntento intento) {
        throw new ExploracionCerradaException();
    }
}
