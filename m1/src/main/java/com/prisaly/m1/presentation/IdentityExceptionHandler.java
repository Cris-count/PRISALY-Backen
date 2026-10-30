package com.prisaly.m1.presentation;

import com.prisaly.m1.domain.rule.IdentityDomainException;
import com.prisaly.m1.presentation.dto.ErrorResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice(basePackages = "com.prisaly.m1")
public class IdentityExceptionHandler {

    @ExceptionHandler(IdentityDomainException.class)
    public ResponseEntity<ErrorResponse> handleDomain(IdentityDomainException ex) {
        HttpStatus status = switch (ex.code()) {
            case "AUTH_FAILED" -> HttpStatus.UNAUTHORIZED;
            case "DUPLICATE_EMAIL" -> HttpStatus.CONFLICT;
            case "UNAUTHENTICATED" -> HttpStatus.UNAUTHORIZED;
            case "USER_INACTIVE", "USER_NOT_FOUND" -> HttpStatus.FORBIDDEN;
            default -> HttpStatus.BAD_REQUEST;
        };
        return ResponseEntity.status(status).body(new ErrorResponse(ex.code(), ex.getMessage()));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleValidation(MethodArgumentNotValidException ex) {
        return ResponseEntity.badRequest()
                .body(new ErrorResponse("VALIDATION_ERROR", "Request validation failed"));
    }
}
