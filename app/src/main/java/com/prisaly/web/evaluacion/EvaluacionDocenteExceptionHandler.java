package com.prisaly.web.evaluacion;

import com.prisaly.m3.api.AutoriaCasoNoDisponibleException;
import com.prisaly.m4.api.ConsultaProgramacionNoDisponibleException;
import com.prisaly.m6.application.AccesoEvaluacionDenegadoException;
import com.prisaly.m6.application.EvaluacionExistenteException;
import com.prisaly.m6.application.EvaluacionNoDisponibleException;
import com.prisaly.m6.application.PreparacionContextoException;
import com.prisaly.m6.domain.strategy.EvaluacionRechazadaException;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.dao.DataAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

/**
 * Error mapping of CONTRATOS-HTTP-M6-55, only for {@link EvaluacionDocenteController}. Messages are fixed texts:
 * no exception message, identifier, capability or SQL detail reaches the response.
 */
@RestControllerAdvice(assignableTypes = {EvaluacionDocenteController.class, LiberacionCalificacionController.class})
@Order(Ordered.HIGHEST_PRECEDENCE)
@ConditionalOnProperty(prefix = "prisaly.m6.evaluacion", name = "enabled", havingValue = "true")
public class EvaluacionDocenteExceptionHandler {

    @ExceptionHandler({EvaluacionDocenteController.SolicitudEvaluacionInvalidaException.class,
            MethodArgumentTypeMismatchException.class, HttpMessageNotReadableException.class,
            HttpMediaTypeNotSupportedException.class})
    ResponseEntity<ErrorEvaluacionHttp> invalida(Exception ex) {
        return error(HttpStatus.BAD_REQUEST, "SOLICITUD_INVALIDA", "La solicitud no es valida", null);
    }

    @ExceptionHandler(AccesoEvaluacionDenegadoException.class)
    ResponseEntity<ErrorEvaluacionHttp> denegado(AccesoEvaluacionDenegadoException ex) {
        return denegado();
    }

    /** N2 (DC-87): releasing an attempt without a grade; same body as the teacher query's 404. */
    @ExceptionHandler(com.prisaly.config.m6.LiberacionCalificacionM6.CalificacionNoEncontradaException.class)
    ResponseEntity<ErrorEvaluacionHttp> sinCalificacion(RuntimeException ex) {
        return error(HttpStatus.NOT_FOUND, "CALIFICACION_NO_ENCONTRADA", "El intento no tiene calificacion", null);
    }

    @ExceptionHandler(EvaluacionExistenteException.class)
    ResponseEntity<ErrorEvaluacionHttp> existente(EvaluacionExistenteException ex) {
        return error(HttpStatus.CONFLICT, EvaluacionExistenteException.CODIGO,
                "El intento ya tiene su calificacion; no se sobrescribe", null);
    }

    @ExceptionHandler(EvaluacionRechazadaException.class)
    ResponseEntity<ErrorEvaluacionHttp> rechazada(EvaluacionRechazadaException ex) {
        return rechazo(ex.codigo());
    }

    @ExceptionHandler(PreparacionContextoException.class)
    ResponseEntity<ErrorEvaluacionHttp> preparacion(PreparacionContextoException ex) {
        return switch (ex.codigo()) {
            case INTENTO_NO_FINALIZADO -> error(HttpStatus.CONFLICT, "INTENTO_NO_FINALIZADO",
                    "Solo se evaluan intentos finalizados", null);
            case INTENTO_INEXISTENTE -> denegado();
            case UUID_INTENTO_INCONSISTENTE, CONTEXTO_INEXISTENTE, CASO_INCONSISTENTE, REFERENCIAS_INCONSISTENTES ->
                    rechazo("CONTEXTO_INCONSISTENTE");
            case EMISOR_AUSENTE, EMISOR_NO_DISPONIBLE, CAPACIDAD_INVALIDA, FALLO_CONSULTA_INTENTO,
                    FALLO_CONSULTA_CONTEXTO -> noDisponible();
        };
    }

    @ExceptionHandler({EvaluacionNoDisponibleException.class, DataAccessException.class,
            AutoriaCasoNoDisponibleException.class, ConsultaProgramacionNoDisponibleException.class})
    ResponseEntity<ErrorEvaluacionHttp> tecnico(RuntimeException ex) {
        return noDisponible();
    }

    /** Remaining IllegalStateException of the chain: incoherent prepared context (EntradaEvaluacionMapper). */
    @ExceptionHandler(IllegalStateException.class)
    ResponseEntity<ErrorEvaluacionHttp> incoherente(IllegalStateException ex) {
        return rechazo("CONTEXTO_INCONSISTENTE");
    }

    private static ResponseEntity<ErrorEvaluacionHttp> rechazo(String motivo) {
        return error(HttpStatus.UNPROCESSABLE_CONTENT, "EVALUACION_RECHAZADA",
                "La evaluacion fue rechazada; no se registro calificacion", motivo);
    }

    private static ResponseEntity<ErrorEvaluacionHttp> denegado() {
        return error(HttpStatus.FORBIDDEN, "ACCESO_DENEGADO", "Acceso denegado", null);
    }

    private static ResponseEntity<ErrorEvaluacionHttp> noDisponible() {
        return error(HttpStatus.SERVICE_UNAVAILABLE, "EVALUACION_NO_DISPONIBLE",
                "La evaluacion no esta disponible temporalmente", null);
    }

    private static ResponseEntity<ErrorEvaluacionHttp> error(HttpStatus estado, String codigo, String mensaje,
            String motivo) {
        return ResponseEntity.status(estado).body(new ErrorEvaluacionHttp(codigo, mensaje, motivo));
    }
}
