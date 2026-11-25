package com.prisaly.web.reporte;

import com.prisaly.m4.api.ConsultaProgramacionNoDisponibleException;
import com.prisaly.m4.api.ReglaNegocioException;
import com.prisaly.m4.presentation.dto.ErrorResponse;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.CacheControl;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

/**
 * DC-BE-CIERRE-FUNCIONAL-72 CANDIDATE (RF-16): {@code GET /api/v1/docente/sesiones/{idSesion}/reporte-participacion}
 * downloads the participation report of one owned session as {@code text/csv; charset=UTF-8} (attachment,
 * {@code no-store}). Exists only with {@code prisaly.m7.reportes.participacion.enabled=true} (shipped false: 404).
 *
 * <p>Errors are JSON {@code {"code","message"}} with M4's codes and statuses: 401 {@code UNAUTHENTICATED} (no token,
 * Spring Security) or {@code M4-NO-AUTENTICADO}; 403 {@code M4-SIN-PERMISO} (no GESTIONAR_SESION) and
 * {@code M4-SESION-AJENA} (another teacher's session); 404 {@code M4-SESION-NO-ENCONTRADA}; 400
 * {@code M4-SOLICITUD-INVALIDA} (malformed id); 503 when the institutional provider is unavailable.
 */
@RestController
@ConditionalOnProperty(name = ReporteParticipacionController.PROPIEDAD, havingValue = "true")
public class ReporteParticipacionController {
    public static final String PROPIEDAD = "prisaly.m7.reportes.participacion.enabled";
    static final MediaType TEXT_CSV_UTF8 = new MediaType("text", "csv", StandardCharsets.UTF_8);

    private static final Logger log = LoggerFactory.getLogger(ReporteParticipacionController.class);

    private final ReporteParticipacionService servicio;

    public ReporteParticipacionController(ReporteParticipacionService servicio) {
        this.servicio = servicio;
    }

    @GetMapping("/v1/docente/sesiones/{idSesion}/reporte-participacion")
    public ResponseEntity<byte[]> descargar(@PathVariable UUID idSesion) {
        var reporte = servicio.generar(idSesion);
        return ResponseEntity.ok()
                .contentType(TEXT_CSV_UTF8)
                .cacheControl(CacheControl.noStore())
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.attachment().filename(reporte.nombreArchivo()).build().toString())
                .body(reporte.contenido());
    }

    @ExceptionHandler(ReglaNegocioException.class)
    ResponseEntity<ErrorResponse> regla(ReglaNegocioException ex) {
        HttpStatus estado = switch (ex.getCodigo()) {
            case "M4-NO-AUTENTICADO" -> HttpStatus.UNAUTHORIZED;
            case "M4-SIN-PERMISO", "M4-SESION-AJENA" -> HttpStatus.FORBIDDEN;
            case "M4-SESION-NO-ENCONTRADA" -> HttpStatus.NOT_FOUND;
            case "M4-PROVEEDOR-NO-DISPONIBLE" -> HttpStatus.SERVICE_UNAVAILABLE;
            default -> HttpStatus.INTERNAL_SERVER_ERROR;
        };
        if (estado.is5xxServerError() && estado != HttpStatus.SERVICE_UNAVAILABLE) {
            log.error("Error no esperado en el reporte de participacion: {}", ex.getCodigo());
            return json(estado, ErrorResponse.of("M4-ERROR-INTERNO", "Error interno del servidor"));
        }
        return json(estado, ErrorResponse.of(ex.getCodigo(), ex.getMessage()));
    }

    @ExceptionHandler(ConsultaProgramacionNoDisponibleException.class)
    ResponseEntity<ErrorResponse> noDisponible(ConsultaProgramacionNoDisponibleException ex) {
        return json(HttpStatus.SERVICE_UNAVAILABLE,
                ErrorResponse.of(ConsultaProgramacionNoDisponibleException.CODIGO, ex.getMessage()));
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    ResponseEntity<ErrorResponse> idInvalido() {
        return json(HttpStatus.BAD_REQUEST, ErrorResponse.of("M4-SOLICITUD-INVALIDA",
                "Solicitud inválida: revise identificadores, cabeceras y cuerpo"));
    }

    private static ResponseEntity<ErrorResponse> json(HttpStatus estado, ErrorResponse cuerpo) {
        return ResponseEntity.status(estado).contentType(MediaType.APPLICATION_JSON).cacheControl(CacheControl.noStore())
                .body(cuerpo);
    }
}
