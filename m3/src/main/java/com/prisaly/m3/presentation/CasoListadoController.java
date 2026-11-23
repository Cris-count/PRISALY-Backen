package com.prisaly.m3.presentation;

import com.prisaly.m3.application.port.CasoAuthenticationException;
import com.prisaly.m3.application.service.CasoDocenteConsultaService;
import com.prisaly.m3.application.service.CasoDocenteListadoService;
import com.prisaly.m3.domain.rule.CasoDomainException;
import com.prisaly.m3.presentation.dto.CasoDocenteConsultaResponse;
import java.util.List;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * DC-67 CANDIDATE: {@code GET /v1/casos} → {@code {"items":[{idCaso, titulo, tiempoEstimadoSegundos, estado}]}}, the
 * current teacher's own visible cases. Not published: exists only with {@code prisaly.m3.listado.enabled=true}.
 * 401 {@code UNAUTHENTICATED}, 403 {@code FORBIDDEN}, 503 {@code CASE_AUTHORIZATION_UNAVAILABLE}.
 */
@RestController
@ConditionalOnProperty(name = "prisaly.m3.listado.enabled", havingValue = "true")
@RequestMapping(value = "/v1/casos", produces = MediaType.APPLICATION_JSON_VALUE)
public class CasoListadoController {
    public record Listado(List<CasoDocenteConsultaResponse> items) { }
    public record ErrorResponse(String code, String message) { }

    private final CasoDocenteListadoService servicio;

    public CasoListadoController(CasoDocenteListadoService servicio) {
        this.servicio = servicio;
    }

    @GetMapping
    public ResponseEntity<Listado> listar() {
        var items = servicio.listarPropios().stream().map(CasoDocenteConsultaResponse::from).toList();
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(new Listado(items));
    }

    @ExceptionHandler(CasoAuthenticationException.class)
    ResponseEntity<ErrorResponse> noAutenticado() {
        return ResponseEntity.status(401).body(new ErrorResponse("UNAUTHENTICATED", "Authentication required"));
    }

    @ExceptionHandler(CasoDomainException.class)
    ResponseEntity<ErrorResponse> dominio(CasoDomainException ex) {
        if (CasoDocenteConsultaService.ACCESO_DENEGADO.equals(ex.code()) || "FORBIDDEN".equals(ex.code())) {
            return ResponseEntity.status(403).body(new ErrorResponse("FORBIDDEN", "Access denied"));
        }
        return ResponseEntity.status(503).body(new ErrorResponse("CASE_AUTHORIZATION_UNAVAILABLE",
                "Institutional verification unavailable"));
    }
}
