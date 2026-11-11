package com.prisaly.m1.presentation;

import com.prisaly.m1.application.service.AutenticacionProtegidaService;
import com.prisaly.m1.presentation.dto.ErrorResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/** DM1-06 (DC-M1-04): HTTP 429 with Retry-After, in the current {code, message} format (DM1-05). */
@RestControllerAdvice(assignableTypes = PersonalAuthController.class)
public class LimiteIntentosExceptionHandler {

    @ExceptionHandler(AutenticacionProtegidaService.AutenticacionLimitadaException.class)
    public ResponseEntity<ErrorResponse> limitada(AutenticacionProtegidaService.AutenticacionLimitadaException ex) {
        return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                .header(HttpHeaders.RETRY_AFTER, Long.toString(ex.reintentarEnSegundos()))
                .body(new ErrorResponse("AUTH_RATE_LIMITED", "Too many authentication attempts"));
    }
}
