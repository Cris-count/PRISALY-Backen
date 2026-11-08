package com.prisaly.m3.presentation;

import com.prisaly.m3.application.port.CasoAuthenticationException;
import com.prisaly.m3.domain.rule.CasoDomainException;
import com.prisaly.m3.presentation.dto.InvalidCasoRequestException;
import java.sql.SQLException;
import java.util.ArrayDeque;
import java.util.Collections;
import java.util.IdentityHashMap;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice(assignableTypes = CasoCreationController.class)
public final class CasoCreationExceptionHandler {
    public record ErrorResponse(String code, String message) { }

    @ExceptionHandler(CasoAuthenticationException.class)
    public ResponseEntity<ErrorResponse> unauthenticated() {
        return error(401, "UNAUTHENTICATED", "Authentication required");
    }

    @ExceptionHandler({InvalidCasoRequestException.class, HttpMessageNotReadableException.class})
    public ResponseEntity<ErrorResponse> invalidRequest() {
        return error(400, "INVALID_CASE_REQUEST", "Invalid case creation request");
    }

    @ExceptionHandler(CasoDomainException.class)
    public ResponseEntity<ErrorResponse> domain(CasoDomainException ex) {
        return switch (ex.code()) {
            case "FORBIDDEN" -> error(403, "FORBIDDEN", "Case creation is not authorized");
            case "BUILDER_INCOMPLETE", "INVALID_ESTIMATED_TIME" ->
                    error(400, "INVALID_CASE_REQUEST", "Invalid case creation request");
            default -> error(500, "CASE_CREATION_FAILED", "Case creation failed");
        };
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> failure(Exception ex) {
        var pending = new ArrayDeque<Throwable>();
        var visited = Collections.newSetFromMap(new IdentityHashMap<Throwable, Boolean>());
        boolean duplicate = false;
        pending.add(ex);
        while (!pending.isEmpty()) {
            Throwable cause = pending.removeFirst();
            if (!visited.add(cause)) { continue; }
            if (cause instanceof SQLException sql) {
                String state = sql.getSQLState();
                // A duplicate must not hide a connection, integrity or unknown SQL failure.
                if (state != null && !state.isEmpty()) {
                    if (!"23505".equals(state)) {
                        return error(500, "CASE_CREATION_FAILED", "Case creation failed");
                    }
                    duplicate = true;
                }
                if (sql.getNextException() != null) { pending.addLast(sql.getNextException()); }
            }
            if (cause.getCause() != null) { pending.addLast(cause.getCause()); }
        }
        if (duplicate) {
            return error(409, "CASE_CONFLICT", "Case creation conflicts with an existing record");
        }
        // FK failures and infrastructure failures are not automatically client errors.
        return error(500, "CASE_CREATION_FAILED", "Case creation failed");
    }

    private static ResponseEntity<ErrorResponse> error(int status, String code, String message) {
        return ResponseEntity.status(status).body(new ErrorResponse(code, message));
    }
}
