package com.prisaly.m1.presentation;

import com.prisaly.m1.api.IdentityDomainException;
import com.prisaly.m1.presentation.dto.ErrorResponse;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/** P-78 (DC-79): HTTP mapping of {@link AdministracionEstudiantesController}, {code, message} format (DM1-05). */
@Order(Ordered.HIGHEST_PRECEDENCE)
@RestControllerAdvice(assignableTypes = AdministracionEstudiantesController.class)
public class AdministracionEstudiantesExceptionHandler {

    @ExceptionHandler(IdentityDomainException.class)
    public ResponseEntity<ErrorResponse> dominio(IdentityDomainException ex) {
        HttpStatus status = switch (ex.code()) {
            case "UNAUTHENTICATED" -> HttpStatus.UNAUTHORIZED;
            case "ADMIN_REQUIRED" -> HttpStatus.FORBIDDEN;
            case "DUPLICATE_EMAIL" -> HttpStatus.CONFLICT;
            case "STUDENT_ROLE_NOT_CONFIGURED", "ROLE_NOT_FOUND" -> HttpStatus.SERVICE_UNAVAILABLE;
            default -> HttpStatus.BAD_REQUEST;
        };
        return respuesta(status, ex.code(), ex.getMessage());
    }

    /** Only a duplicate key (concurrent registration of the same email); other integrity failures are not masked. */
    @ExceptionHandler(DuplicateKeyException.class)
    public ResponseEntity<ErrorResponse> duplicado() {
        return respuesta(HttpStatus.CONFLICT, "DUPLICATE_EMAIL", "Email already registered");
    }

    @ExceptionHandler({MethodArgumentNotValidException.class, HttpMessageNotReadableException.class,
            MissingServletRequestParameterException.class})
    public ResponseEntity<ErrorResponse> invalida() {
        return respuesta(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", "Request validation failed");
    }

    private static ResponseEntity<ErrorResponse> respuesta(HttpStatus status, String code, String message) {
        return ResponseEntity.status(status).cacheControl(CacheControl.noStore()).body(new ErrorResponse(code, message));
    }
}
