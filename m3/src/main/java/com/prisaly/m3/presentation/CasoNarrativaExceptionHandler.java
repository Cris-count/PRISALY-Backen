package com.prisaly.m3.presentation;

import com.prisaly.m3.application.port.CasoAuthenticationException;
import com.prisaly.m3.application.service.GuionNarrativoService;
import com.prisaly.m3.domain.narrativa.GuionNarrativoInvalidoException;
import com.prisaly.m3.domain.rule.CasoDomainException;

import java.sql.SQLException;
import java.util.List;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.dao.DataAccessException;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/** Errores de {@link CasoNarrativaController} con el cuerpo {@code {code, message}} de M3 (más {@code errores} en 422). */
@RestControllerAdvice(assignableTypes = CasoNarrativaController.class)
// Same switch as M3NarrativaConfig.PROPIEDAD (presentation must not import infrastructure).
@ConditionalOnProperty(name = "prisaly.m3.narrativa.publicacion.enabled", havingValue = "true")
public final class CasoNarrativaExceptionHandler {
    public record ErrorResponse(String code, String message) { }

    public record ErrorValidacion(String code, String message, List<GuionNarrativoInvalidoException.Error> errores) { }

    @ExceptionHandler(CasoAuthenticationException.class)
    public ResponseEntity<ErrorResponse> sinAutenticacion() { return error(401, "UNAUTHENTICATED"); }

    @ExceptionHandler({IllegalArgumentException.class, HttpMessageNotReadableException.class})
    public ResponseEntity<ErrorResponse> invalido() { return error(400, "INVALID_CASE_REQUEST"); }

    @ExceptionHandler(GuionNarrativoInvalidoException.class)
    public ResponseEntity<ErrorValidacion> guionInvalido(GuionNarrativoInvalidoException ex) {
        return ResponseEntity.status(422).cacheControl(CacheControl.noStore()).body(new ErrorValidacion(
                GuionNarrativoInvalidoException.CODIGO, "The narrative script is not valid", ex.errores()));
    }

    @ExceptionHandler(CasoDomainException.class)
    public ResponseEntity<ErrorResponse> dominio(CasoDomainException ex) {
        return switch (ex.code()) {
            case "FORBIDDEN" -> error(403, "FORBIDDEN");
            case "CASE_NOT_FOUND" -> error(404, "CASE_NOT_FOUND");
            case GuionNarrativoService.CODE_CONTENIDO_BLOQUEADO, GuionNarrativoService.CODE_CASO_NO_PUBLICABLE,
                    GuionNarrativoService.CODE_SIN_BORRADOR, GuionNarrativoService.CODE_CASO_NO_EDITABLE -> error(409, ex.code());
            case "CASE_AUTHORIZATION_UNAVAILABLE" -> error(503, ex.code());
            default -> error(500, "INTERNAL_ERROR");
        };
    }

    /** A concurrent programming or a direct violation reaches the database guards: never a partial write. */
    @ExceptionHandler(DataAccessException.class)
    public ResponseEntity<ErrorResponse> baseDeDatos(DataAccessException ex) {
        for (Throwable t = ex; t != null; t = t.getCause() == t ? null : t.getCause()) {
            if (t instanceof SQLException sql && "23514".equals(sql.getSQLState())) {
                return error(409, GuionNarrativoService.CODE_CONTENIDO_BLOQUEADO);
            }
            if (t instanceof SQLException sql && "23505".equals(sql.getSQLState())) {
                return error(409, "CONTENT_CONFLICT");
            }
        }
        return error(500, "INTERNAL_ERROR");
    }

    private static ResponseEntity<ErrorResponse> error(int estado, String code) {
        return ResponseEntity.status(estado).cacheControl(CacheControl.noStore())
                .body(new ErrorResponse(code, "Narrative script operation could not be completed"));
    }
}
