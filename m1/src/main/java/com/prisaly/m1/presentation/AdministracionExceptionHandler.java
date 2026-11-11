package com.prisaly.m1.presentation;

import com.prisaly.m1.api.IdentityDomainException;
import com.prisaly.m1.presentation.dto.ErrorResponse;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

/**
 * DC-M1-04: HTTP mapping for the administrative API and the password change.
 * Keeps the current {code, message} format (DM1-05). Takes precedence over {@link IdentityExceptionHandler} only for
 * these controllers, so the existing routes keep their validated mapping.
 */
@Order(Ordered.HIGHEST_PRECEDENCE)
@RestControllerAdvice(assignableTypes = {AdministracionController.class, PersonalContrasenaController.class})
public class AdministracionExceptionHandler {

    @ExceptionHandler(IdentityDomainException.class)
    public ResponseEntity<ErrorResponse> dominio(IdentityDomainException ex) {
        HttpStatus status = switch (ex.code()) {
            case "AUTH_FAILED", "UNAUTHENTICATED" -> HttpStatus.UNAUTHORIZED;
            case "ADMIN_REQUIRED", "PERMISSION_SELF_ASSIGNMENT_NOT_ALLOWED", "PERMISSION_ACTOR_REQUIRED",
                    "PERMISSION_ACTOR_INVALID", "PERMISSION_ACTOR_NOT_ADMIN", "FORBIDDEN", "USER_INACTIVE" ->
                    HttpStatus.FORBIDDEN;
            case "USER_NOT_FOUND", "PERMISSION_NOT_FOUND", "ROLE_NOT_FOUND" -> HttpStatus.NOT_FOUND;
            case "DUPLICATE_ASSIGNMENT", "DUPLICATE_EMAIL" -> HttpStatus.CONFLICT;
            case "PERMISSION_NOT_ALLOWED_FOR_STUDENT", "PASSWORD_TOO_SHORT", "PASSWORD_TOO_LONG", "PASSWORD_COMMON",
                    "PASSWORD_CONTAINS_EMAIL" -> HttpStatus.UNPROCESSABLE_ENTITY;
            case "TEACHER_ROLE_NOT_CONFIGURED" -> HttpStatus.SERVICE_UNAVAILABLE;
            default -> HttpStatus.BAD_REQUEST;
        };
        return ResponseEntity.status(status).body(new ErrorResponse(ex.code(), ex.getMessage()));
    }

    @ExceptionHandler({MethodArgumentNotValidException.class, HttpMessageNotReadableException.class,
            MethodArgumentTypeMismatchException.class})
    public ResponseEntity<ErrorResponse> invalida() {
        return ResponseEntity.badRequest().body(new ErrorResponse("VALIDATION_ERROR", "Request validation failed"));
    }
}
