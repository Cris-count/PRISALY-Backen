package com.prisaly.web.dc92;

import com.prisaly.config.dc92.ParticipacionesDocenteDc92;
import com.prisaly.m4.api.ConsultaProgramacionNoDisponibleException;
import com.prisaly.m4.api.ReglaNegocioException;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

/**
 * DC-92 teacher routes: error body {@code {"code","message"}} with M4's codes and statuses for the reused session rule
 * (401 M4-NO-AUTENTICADO, 403 M4-SIN-PERMISO / M4-SESION-AJENA, 404 M4-SESION-NO-ENCONTRADA, 503) plus the DC-92
 * rejections. No internal detail is exposed.
 */
@RestControllerAdvice(basePackages = "com.prisaly.web.dc92")
public class ErroresDc92 {

    public record ErrorHttp(String code, String message) { }

    @ExceptionHandler(ReglaNegocioException.class)
    ResponseEntity<ErrorHttp> m4(ReglaNegocioException ex) {
        HttpStatus estado = switch (ex.getCodigo()) {
            case "M4-NO-AUTENTICADO" -> HttpStatus.UNAUTHORIZED;
            case "M4-SIN-PERMISO", "M4-SESION-AJENA" -> HttpStatus.FORBIDDEN;
            case "M4-SESION-NO-ENCONTRADA" -> HttpStatus.NOT_FOUND;
            case "M4-PROVEEDOR-NO-DISPONIBLE" -> HttpStatus.SERVICE_UNAVAILABLE;
            default -> HttpStatus.INTERNAL_SERVER_ERROR;
        };
        return estado == HttpStatus.INTERNAL_SERVER_ERROR
                ? json(estado, new ErrorHttp("ERROR_INTERNO", "Error interno del servidor"))
                : json(estado, new ErrorHttp(ex.getCodigo(), ex.getMessage()));
    }

    @ExceptionHandler(ParticipacionesDocenteDc92.RechazoDc92.class)
    ResponseEntity<ErrorHttp> dc92(ParticipacionesDocenteDc92.RechazoDc92 ex) {
        return json(HttpStatus.valueOf(ex.estado()), new ErrorHttp(ex.codigo(), ex.getMessage()));
    }

    @ExceptionHandler(ConsultaProgramacionNoDisponibleException.class)
    ResponseEntity<ErrorHttp> noDisponible() {
        return json(HttpStatus.SERVICE_UNAVAILABLE, new ErrorHttp(ConsultaProgramacionNoDisponibleException.CODIGO,
                "Servicio no disponible"));
    }

    @ExceptionHandler({MethodArgumentTypeMismatchException.class, HttpMessageNotReadableException.class})
    ResponseEntity<ErrorHttp> invalida() {
        return json(HttpStatus.BAD_REQUEST, new ErrorHttp("VALIDACION", "Solicitud invalida"));
    }

    private static ResponseEntity<ErrorHttp> json(HttpStatus estado, ErrorHttp cuerpo) {
        return ResponseEntity.status(estado).contentType(MediaType.APPLICATION_JSON).cacheControl(CacheControl.noStore())
                .body(cuerpo);
    }
}
