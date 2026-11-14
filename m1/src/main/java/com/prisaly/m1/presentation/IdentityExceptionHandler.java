package com.prisaly.m1.presentation;

import com.prisaly.m1.api.IdentityDomainException;
import com.prisaly.m1.application.port.EmisorTokenAccesoPort;
import com.prisaly.m1.presentation.dto.ErrorResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * Manejador de M1. AUTH_FAILED, UNAUTHENTICATED y STUDENT_ACCESS_DENIED salen 401.
 * USER_INACTIVE, USER_NOT_FOUND, FORBIDDEN y STUDENT_IDENTITY_UNAVAILABLE salen 403.
 * Un cuerpo que no cumple las anotaciones sale 400 VALIDATION_ERROR.
 * No incluye la contraseña ni la traza en el JSON.
 */
@RestControllerAdvice(basePackages = "com.prisaly.m1")
public class IdentityExceptionHandler {

    /** No quedan huecos para tokens provisionales. Responde 503. */
    @ExceptionHandler(EmisorTokenAccesoPort.CapacidadAgotadaException.class)
    public ResponseEntity<ErrorResponse> handleTokenCapacity() {
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                .body(new ErrorResponse("AUTH_CAPACITY_EXCEEDED", "Authentication temporarily unavailable"));
    }

    /** Credenciales o sesión fallida van a 401; cuenta inactiva o sin permiso, a 403. */
    @ExceptionHandler(IdentityDomainException.class)
    public ResponseEntity<ErrorResponse> handleDomain(IdentityDomainException ex) {
        HttpStatus status = switch (ex.code()) {
            case "AUTH_FAILED", "UNAUTHENTICATED", "STUDENT_ACCESS_DENIED" -> HttpStatus.UNAUTHORIZED;
            case "DUPLICATE_EMAIL" -> HttpStatus.CONFLICT;
            case "USER_INACTIVE", "USER_NOT_FOUND", "FORBIDDEN",
                    "STUDENT_IDENTITY_UNAVAILABLE" -> HttpStatus.FORBIDDEN;
            default -> HttpStatus.BAD_REQUEST;
        };
        return ResponseEntity.status(status).body(new ErrorResponse(ex.code(), ex.getMessage()));
    }

    /** Cuerpo que no pasa las anotaciones de validación. Responde 400 VALIDATION_ERROR. */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleValidation(MethodArgumentNotValidException ex) {
        return ResponseEntity.badRequest()
                .body(new ErrorResponse("VALIDATION_ERROR", "Request validation failed"));
    }
}
