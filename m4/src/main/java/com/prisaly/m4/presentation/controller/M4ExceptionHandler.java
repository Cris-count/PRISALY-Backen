package com.prisaly.m4.presentation.controller;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.dao.PessimisticLockingFailureException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

import jakarta.persistence.OptimisticLockException;
import jakarta.persistence.PessimisticLockException;
import jakarta.persistence.LockTimeoutException;

import com.prisaly.m4.domain.rule.ReglaNegocioException;
import com.prisaly.m4.domain.rule.SesionOperacionNoPermitidaException;
import com.prisaly.m4.presentation.dto.ErrorResponse;
import com.prisaly.m4.api.ConsultaProgramacionNoDisponibleException;

/**
 * Manejo centralizado de excepciones para M4.
 * Mapea excepciones de dominio a códigos HTTP apropiados.
 */
@RestControllerAdvice(basePackages = "com.prisaly.m4.presentation")
public class M4ExceptionHandler extends ResponseEntityExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(M4ExceptionHandler.class);

    @ExceptionHandler(ConsultaProgramacionNoDisponibleException.class)
    public ResponseEntity<ErrorResponse> handleConsultaNoDisponible(ConsultaProgramacionNoDisponibleException ex) {
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                .body(ErrorResponse.of(ConsultaProgramacionNoDisponibleException.CODIGO, ex.getMessage()));
    }

    @ExceptionHandler(ReglaNegocioException.class)
    public ResponseEntity<ErrorResponse> handleReglaNegocio(ReglaNegocioException ex) {
        HttpStatus status = mapearCodigo(ex.getCodigo());
        return ResponseEntity.status(status)
                .body(ErrorResponse.of(ex.getCodigo(), ex.getMessage()));
    }

    @ExceptionHandler(SesionOperacionNoPermitidaException.class)
    public ResponseEntity<ErrorResponse> handleOperacionNoPermitida(
            SesionOperacionNoPermitidaException ex) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(ErrorResponse.of("M4-OPERACION-NO-PERMITIDA", ex.getMessage()));
    }

    @Override
    protected ResponseEntity<Object> handleMethodArgumentNotValid(MethodArgumentNotValidException ex,
            HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        String mensaje = ex.getBindingResult().getFieldErrors().stream()
                .map(fe -> fe.getField() + ": " + fe.getDefaultMessage())
                .reduce((a, b) -> a + "; " + b)
                .orElse("Solicitud inválida");
        return new ResponseEntity<>(ErrorResponse.of("M4-VALIDACION", mensaje), headers, status);
    }

    /** Conserva el estado y las cabeceras estándar de Spring (Allow, Accept, etc.). */
    @Override
    protected ResponseEntity<Object> handleExceptionInternal(Exception ex, Object body,
            HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        String codigo;
        String mensaje;
        switch (status.value()) {
            case 400 -> {
                codigo = "M4-SOLICITUD-INVALIDA";
                mensaje = "Solicitud inválida: revise identificadores, cabeceras y cuerpo";
            }
            case 404 -> {
                codigo = "M4-RECURSO-NO-ENCONTRADO";
                mensaje = "Recurso no encontrado";
            }
            case 405 -> {
                codigo = "M4-METODO-NO-PERMITIDO";
                mensaje = "Método HTTP no permitido";
            }
            case 406 -> {
                codigo = "M4-RESPUESTA-NO-ACEPTABLE";
                mensaje = "El formato de respuesta solicitado no está disponible";
            }
            case 415 -> {
                codigo = "M4-TIPO-CONTENIDO-NO-SOPORTADO";
                mensaje = "Tipo de contenido no soportado";
            }
            default -> {
                codigo = status.is5xxServerError() ? "M4-ERROR-INTERNO" : "M4-ERROR-HTTP";
                mensaje = status.is5xxServerError() ? "Error interno del servidor" : "Solicitud rechazada";
            }
        }
        if (status.is5xxServerError()) {
            log.error("Error HTTP interno en M4", ex);
        }
        return new ResponseEntity<>(ErrorResponse.of(codigo, mensaje), headers, status);
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ErrorResponse> handleIntegridad(DataIntegrityViolationException ex) {
        for (Throwable causa = ex; causa != null; causa = causa.getCause()) {
            if (causa instanceof org.hibernate.exception.ConstraintViolationException restriccion
                    && "23505".equals(restriccion.getSQLState())) {
                String nombre = restriccion.getConstraintName();
                if ("uq_sesion_caso_docente_fechas".equals(nombre)) {
                    return ResponseEntity.status(HttpStatus.CONFLICT).body(ErrorResponse.of(
                            "M4-SESION-DUPLICADA", "Ya existe una sesión con el mismo caso, docente y fechas"));
                }
                if ("uq_convocatoria_sesion_estudiante".equals(nombre)
                        || "uq_convocatoria_idx1".equals(nombre)) {
                    return ResponseEntity.status(HttpStatus.CONFLICT).body(ErrorResponse.of(
                            "M4-CONVOCATORIA-DUPLICADA", "El estudiante ya está convocado a esta sesión"));
                }
            }
        }
        // Un error SQL desconocido no se presenta falsamente como duplicación.
        return handleGeneral(ex);
    }

    @ExceptionHandler({OptimisticLockingFailureException.class, OptimisticLockException.class,
            PessimisticLockingFailureException.class, PessimisticLockException.class, LockTimeoutException.class})
    public ResponseEntity<ErrorResponse> handleConcurrencia(Exception ex) {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(ErrorResponse.of(
                "M4-CONFLICTO-CONCURRENCIA", "La operación entró en conflicto con otra modificación; consulte el estado actual"));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleGeneral(Exception ex) {
        log.error("Error interno no controlado en M4", ex);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ErrorResponse.of("M4-ERROR-INTERNO",
                        "Error interno del servidor"));
    }

    private HttpStatus mapearCodigo(String codigo) {
        return switch (codigo) {
            case "M4-NO-AUTENTICADO" -> HttpStatus.UNAUTHORIZED;
            case "M4-ACCESO-LEGADO-RETIRADO" -> HttpStatus.GONE;
            case "M4-BLOQUEO-ACADEMICO-NO-CONFIRMADO" -> HttpStatus.SERVICE_UNAVAILABLE;
            case "M4-PROVEEDOR-NO-DISPONIBLE" -> HttpStatus.SERVICE_UNAVAILABLE;
            case "M4-CASO-NO-PUBLICADO",
                 "M4-SESION-DUPLICADA",
                 "M4-CONVOCATORIA-DUPLICADA",
                 "M4-SESION-CON-PARTICIPACIONES",
                 "M4-SESION-CON-CONVOCATORIAS",
                 "M4-CONVOCATORIA-NO-PERMITIDA",
                 "M4-ESTADO-INVALIDO"          -> HttpStatus.CONFLICT;          // 409
            case "M4-SESION-NO-ENCONTRADA"     -> HttpStatus.NOT_FOUND;         // 404
            case "M4-SIN-PERMISO",
                 "M4-SESION-AJENA"             -> HttpStatus.FORBIDDEN;         // 403
            case "M4-FECHA-INVALIDA"           -> HttpStatus.BAD_REQUEST;       // 400
            default                            -> HttpStatus.INTERNAL_SERVER_ERROR;
        };
    }
}
