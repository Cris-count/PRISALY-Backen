package com.prisaly.m1.presentation;

import com.prisaly.m1.api.IdentityDomainException;
import com.prisaly.m1.application.port.EntregaActivacionDocentePort;
import com.prisaly.m1.application.service.AutenticacionProtegidaService;
import com.prisaly.m1.presentation.dto.ErrorResponse;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

/** D-05 (DC-77): HTTP mapping of {@link ActivacionDocenteController} in the {code, message} format (DM1-05). */
@Order(Ordered.HIGHEST_PRECEDENCE)
@RestControllerAdvice(assignableTypes = ActivacionDocenteController.class)
public class ActivacionDocenteExceptionHandler {

    @ExceptionHandler(IdentityDomainException.class)
    public ResponseEntity<ErrorResponse> dominio(IdentityDomainException ex) {
        HttpStatus status = switch (ex.code()) {
            case "UNAUTHENTICATED" -> HttpStatus.UNAUTHORIZED;
            case "ADMIN_REQUIRED" -> HttpStatus.FORBIDDEN;
            case "USER_NOT_FOUND" -> HttpStatus.NOT_FOUND;
            case "ACTIVATION_NOT_ALLOWED" -> HttpStatus.CONFLICT;
            case "PASSWORD_TOO_SHORT", "PASSWORD_TOO_LONG", "PASSWORD_COMMON", "PASSWORD_CONTAINS_EMAIL" ->
                    HttpStatus.UNPROCESSABLE_ENTITY;
            default -> HttpStatus.BAD_REQUEST; // ACTIVATION_INVALID and any other domain rejection
        };
        return respuesta(status, ex.code(), ex.getMessage());
    }

    @ExceptionHandler(AutenticacionProtegidaService.AutenticacionLimitadaException.class)
    public ResponseEntity<ErrorResponse> limitada(AutenticacionProtegidaService.AutenticacionLimitadaException ex) {
        return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                .header(HttpHeaders.RETRY_AFTER, Long.toString(ex.reintentarEnSegundos()))
                .cacheControl(CacheControl.noStore())
                .body(new ErrorResponse("ACTIVATION_RATE_LIMITED", "Too many activation attempts"));
    }

    @ExceptionHandler(EntregaActivacionDocentePort.EntregaFallidaException.class)
    public ResponseEntity<ErrorResponse> entrega() {
        return respuesta(HttpStatus.SERVICE_UNAVAILABLE, "ACTIVATION_DELIVERY_FAILED", "Invitation could not be sent");
    }

    /** Two concurrent issues for the same teacher: the live-token unique index keeps one; the other is told to retry. */
    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ErrorResponse> conflicto() {
        return respuesta(HttpStatus.CONFLICT, "ACTIVATION_CONFLICT", "Concurrent activation change; retry");
    }

    @ExceptionHandler({MethodArgumentNotValidException.class, HttpMessageNotReadableException.class,
            MethodArgumentTypeMismatchException.class})
    public ResponseEntity<ErrorResponse> invalida() {
        return respuesta(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", "Request validation failed");
    }

    private static ResponseEntity<ErrorResponse> respuesta(HttpStatus status, String code, String message) {
        return ResponseEntity.status(status).cacheControl(CacheControl.noStore()).body(new ErrorResponse(code, message));
    }
}
