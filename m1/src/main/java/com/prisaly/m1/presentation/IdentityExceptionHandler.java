package com.prisaly.m1.presentation;

import com.prisaly.m1.api.IdentityDomainException;
import com.prisaly.m1.presentation.dto.ErrorResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice(basePackages = "com.prisaly.m1")
public class IdentityExceptionHandler {

    @ExceptionHandler(com.prisaly.m1.infrastructure.security.ProvisionalAccessTokenStore.CapacityExceededException.class)
    public ResponseEntity<ErrorResponse> handleTokenCapacity() {
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                .body(new ErrorResponse("AUTH_CAPACITY_EXCEEDED", "Authentication temporarily unavailable"));
    }

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

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleValidation(MethodArgumentNotValidException ex) {
        return ResponseEntity.badRequest()
                .body(new ErrorResponse("VALIDATION_ERROR", "Request validation failed"));
    }
}
