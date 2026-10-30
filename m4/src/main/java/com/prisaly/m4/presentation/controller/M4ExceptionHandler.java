package com.prisaly.m4.presentation.controller;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import com.prisaly.m4.domain.rule.ReglaNegocioException;
import com.prisaly.m4.domain.rule.SesionOperacionNoPermitidaException;
import com.prisaly.m4.presentation.dto.ErrorResponse;

/**
 * Manejo centralizado de excepciones para M4.
 * Mapea excepciones de dominio a códigos HTTP apropiados.
 */
@RestControllerAdvice(basePackages = "com.prisaly.m4.presentation")
public class M4ExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(M4ExceptionHandler.class);

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

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleValidation(MethodArgumentNotValidException ex) {
        String mensaje = ex.getBindingResult().getFieldErrors().stream()
                .map(fe -> fe.getField() + ": " + fe.getDefaultMessage())
                .reduce((a, b) -> a + "; " + b)
                .orElse("Solicitud inválida");
        return ResponseEntity.badRequest()
                .body(ErrorResponse.of("M4-VALIDACION", mensaje));
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
            case "M4-CASO-NO-PUBLICADO",
                 "M4-SESION-DUPLICADA",
                 "M4-CONVOCATORIA-DUPLICADA",
                 "M4-SESION-CON-PARTICIPACIONES",
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
