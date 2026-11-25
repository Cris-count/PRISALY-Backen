package com.prisaly.web.evaluacion;

import com.prisaly.config.m6.M6RetroalimentacionDd12Config;
import com.prisaly.config.m6.RetroalimentacionDocenteDd12;
import com.prisaly.m6.application.AccesoEvaluacionDenegadoException;
import com.prisaly.m6.domain.model.RetroalimentacionGeneral;
import java.net.URI;
import java.util.UUID;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * DC-92 DD-12 / RF-26: {@code POST|PUT|GET /api/v1/docente/participaciones/{id}/retroalimentacion} (owner teacher,
 * M6 rule). POST 201 / 409 RETROALIMENTACION_EXISTENTE; PUT 200 / 404; GET 200 / 404; 403 ACCESO_DENEGADO;
 * 400 VALIDACION. The student sees it only with the released grade.
 */
@RestController
@ConditionalOnProperty(name = M6RetroalimentacionDd12Config.PROPIEDAD, havingValue = "true")
@RequestMapping(value = "/v1/docente/participaciones/{idParticipacion}/retroalimentacion",
        produces = MediaType.APPLICATION_JSON_VALUE)
public class RetroalimentacionDocenteController {

    public record SolicitudRetroalimentacion(String contenido) { }
    public record ErrorHttp(String code, String message) { }

    private final RetroalimentacionDocenteDd12 servicio;

    public RetroalimentacionDocenteController(RetroalimentacionDocenteDd12 servicio) {
        this.servicio = servicio;
    }

    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<RetroalimentacionGeneral> registrar(@PathVariable UUID idParticipacion,
            @RequestBody(required = false) SolicitudRetroalimentacion cuerpo) {
        var r = servicio.registrar(idParticipacion, cuerpo == null ? null : cuerpo.contenido());
        return ResponseEntity.created(URI.create("/api/v1/docente/participaciones/" + idParticipacion + "/retroalimentacion"))
                .body(r);
    }

    @PutMapping(consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<RetroalimentacionGeneral> actualizar(@PathVariable UUID idParticipacion,
            @RequestBody(required = false) SolicitudRetroalimentacion cuerpo) {
        return ResponseEntity.ok(servicio.actualizar(idParticipacion, cuerpo == null ? null : cuerpo.contenido()));
    }

    @GetMapping
    public ResponseEntity<RetroalimentacionGeneral> consultar(@PathVariable UUID idParticipacion) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(servicio.consultar(idParticipacion));
    }

    @ExceptionHandler(RetroalimentacionDocenteDd12.RechazoRetroalimentacion.class)
    ResponseEntity<ErrorHttp> rechazo(RetroalimentacionDocenteDd12.RechazoRetroalimentacion ex) {
        return ResponseEntity.status(ex.estado()).body(new ErrorHttp(ex.codigo(), ex.getMessage()));
    }

    @ExceptionHandler(AccesoEvaluacionDenegadoException.class)
    ResponseEntity<ErrorHttp> denegado() {
        return ResponseEntity.status(403).body(new ErrorHttp("ACCESO_DENEGADO", "Acceso denegado"));
    }

    @ExceptionHandler({org.springframework.web.method.annotation.MethodArgumentTypeMismatchException.class,
            org.springframework.http.converter.HttpMessageNotReadableException.class})
    ResponseEntity<ErrorHttp> invalida() {
        return ResponseEntity.badRequest().body(new ErrorHttp("VALIDACION", "Solicitud invalida"));
    }
}
