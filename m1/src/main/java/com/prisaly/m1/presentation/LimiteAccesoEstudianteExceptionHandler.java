package com.prisaly.m1.presentation;

import com.prisaly.m1.application.service.LimiteAccesoEstudianteException;
import com.prisaly.m1.presentation.dto.ErrorResponse;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * D-B2.2: 429 {@code AUTH_RATE_LIMITED} with {@code Retry-After}, the same body whatever the email (DM1-05 format).
 * Malformed JSON on the student-access routes is the same 400 {@code VALIDATION_ERROR}.
 */
@Order(Ordered.HIGHEST_PRECEDENCE)
@RestControllerAdvice(assignableTypes = EstudianteCuentaController.class)
public class LimiteAccesoEstudianteExceptionHandler {

    @ExceptionHandler(LimiteAccesoEstudianteException.class)
    public ResponseEntity<ErrorResponse> limitada(LimiteAccesoEstudianteException ex) {
        return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                .header(HttpHeaders.RETRY_AFTER, Long.toString(ex.segundos()))
                .body(new ErrorResponse("AUTH_RATE_LIMITED", "Too many authentication attempts"));
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ErrorResponse> ilegible() {
        return ResponseEntity.badRequest().body(new ErrorResponse("VALIDATION_ERROR", "Request validation failed"));
    }
}
