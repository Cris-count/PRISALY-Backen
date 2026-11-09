package com.prisaly.m3.presentation;

import com.prisaly.m3.application.port.CasoAuthenticationException;
import com.prisaly.m3.domain.rule.CasoDomainException;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.annotation.*;

@RestControllerAdvice(assignableTypes = {CasoGestionController.class, CasoContenidoController.class})
public final class CasoGestionExceptionHandler {
    public record ErrorResponse(String code, String message) { }
    @ExceptionHandler(CasoAuthenticationException.class)
    public ResponseEntity<ErrorResponse> unauthenticated() { return error(401, "UNAUTHENTICATED"); }
    @ExceptionHandler({IllegalArgumentException.class, HttpMessageNotReadableException.class})
    public ResponseEntity<ErrorResponse> invalid() { return error(400, "INVALID_CASE_REQUEST"); }
    @ExceptionHandler(CasoGestionController.HttpContractUnavailable.class)
    public ResponseEntity<ErrorResponse> unavailable() { return error(503, "PATCH_HTTP_CONTRACT_UNAVAILABLE"); }
    @ExceptionHandler(CasoGestionController.IfMatchRequired.class)
    public ResponseEntity<ErrorResponse> ifMatchRequired() { return error(428, "IF_MATCH_REQUIRED"); }
    @ExceptionHandler(CasoDomainException.class)
    public ResponseEntity<ErrorResponse> domain(CasoDomainException ex) {
        return switch (ex.code()) {
            case "FORBIDDEN", "CASE_READ_DENIED" -> error(403, "FORBIDDEN");
            case "CASE_NOT_FOUND", "CASE_READ_NOT_FOUND" -> error(404, "CASE_NOT_FOUND");
            case "ELEMENT_NOT_IN_CASE" -> error(404, "ELEMENT_NOT_IN_CASE");
            case "SCENARIO_ID_IN_USE", "SCENARIO_ORDER_IN_USE", "QUESTION_ID_IN_USE", "QUESTION_ORDER_IN_USE",
                    "OPTION_ID_IN_USE", "CONTENT_CONFLICT" -> error(409, ex.code());
            case "STALE_CASE" -> error(412, ex.code());
            case "EVALUABLE_CONTENT_LOCKED", "ARCHIVED_CASE_OPERATION_PENDING", "CASE_NOT_EDITABLE" -> error(409, ex.code());
            case "INVALID_ESTIMATED_TIME" -> error(400, "INVALID_CASE_REQUEST");
            default -> error(500, "CASE_OPERATION_FAILED");
        };
    }
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> failure() { return error(500, "CASE_OPERATION_FAILED"); }
    private static ResponseEntity<ErrorResponse> error(int status, String code) {
        return ResponseEntity.status(status).body(new ErrorResponse(code, "Case operation could not be completed"));
    }
}
