package com.prisaly.web.dc92;

import com.prisaly.config.dc92.M5RepeticionDd04Config;
import com.prisaly.config.dc92.RepeticionDocenteDd04;
import com.prisaly.m5.application.repeticion.AutorizacionRepeticionDenegadaException;
import com.prisaly.m5.application.repeticion.AutorizacionRepeticionVigenteException;
import com.prisaly.m5.application.repeticion.ParticipacionNoEncontradaException;
import java.net.URI;
import java.time.Instant;
import java.util.UUID;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * DC-92 DD-04: teacher routes (personnel Bearer). Only the creator of the case (existing RF-25 rule) operates.
 * <ul>
 *   <li>{@code POST /api/v1/docente/participaciones/{id}/repeticion} {@code {motivo}} → 201;</li>
 *   <li>{@code POST /api/v1/docente/participaciones/{id}/intento-valido} {@code {idIntento, motivo}} → 201;</li>
 *   <li>{@code GET /api/v1/docente/participaciones/{id}/repeticion} → 200 history.</li>
 * </ul>
 * Errors {@code {"code","message"}}: 400 VALIDACION, 403 ACCESO_DENEGADO, 404 PARTICIPACION_NO_ENCONTRADA,
 * 409 AUTORIZACION_VIGENTE / REPETICION_AGOTADA, 422 INTENTO_NO_SELECCIONABLE.
 */
@RestController
@ConditionalOnProperty(name = M5RepeticionDd04Config.PROPIEDAD, havingValue = "true")
@RequestMapping(value = "/v1/docente/participaciones/{idParticipacion}", produces = MediaType.APPLICATION_JSON_VALUE)
public class RepeticionDocenteController {

    public record SolicitudRepeticion(String motivo) { }
    public record SolicitudSeleccion(UUID idIntento, String motivo) { }
    public record AutorizacionHttp(UUID idAutorizacion, UUID idParticipacion, UUID idDocenteAutorizador, String motivo,
            Instant fechaAutorizacion, String estadoAutorizacion) { }
    public record ErrorHttp(String code, String message) { }

    private final RepeticionDocenteDd04 repeticion;

    public RepeticionDocenteController(RepeticionDocenteDd04 repeticion) {
        this.repeticion = repeticion;
    }

    @PostMapping(value = "/repeticion", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<AutorizacionHttp> autorizar(@PathVariable UUID idParticipacion,
            @RequestBody(required = false) SolicitudRepeticion cuerpo) {
        var a = repeticion.autorizar(idParticipacion, cuerpo == null ? null : cuerpo.motivo());
        return ResponseEntity.created(URI.create("/api/v1/docente/participaciones/" + idParticipacion + "/repeticion"))
                .body(new AutorizacionHttp(a.idAutorizacion(), a.idParticipacion(), a.idDocenteAutorizador(),
                        a.motivo().orElse(null), a.fechaAutorizacion(), a.estado()));
    }

    @PostMapping(value = "/intento-valido", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<RepeticionDocenteDd04.Seleccion> seleccionar(@PathVariable UUID idParticipacion,
            @RequestBody(required = false) SolicitudSeleccion cuerpo) {
        var s = repeticion.seleccionar(idParticipacion, cuerpo == null ? null : cuerpo.idIntento(),
                cuerpo == null ? null : cuerpo.motivo());
        return ResponseEntity.created(URI.create("/api/v1/docente/participaciones/" + idParticipacion + "/repeticion"))
                .body(s);
    }

    @GetMapping("/repeticion")
    public ResponseEntity<RepeticionDocenteDd04.Historial> historial(@PathVariable UUID idParticipacion) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(repeticion.historial(idParticipacion));
    }

    @ExceptionHandler(RepeticionDocenteDd04.RechazoRepeticion.class)
    ResponseEntity<ErrorHttp> rechazo(RepeticionDocenteDd04.RechazoRepeticion ex) {
        return ResponseEntity.status(ex.estado()).body(new ErrorHttp(ex.codigo(), ex.getMessage()));
    }

    @ExceptionHandler(AutorizacionRepeticionDenegadaException.class)
    ResponseEntity<ErrorHttp> denegada() {
        return ResponseEntity.status(403).body(new ErrorHttp("ACCESO_DENEGADO", "Acceso denegado"));
    }

    @ExceptionHandler(ParticipacionNoEncontradaException.class)
    ResponseEntity<ErrorHttp> noEncontrada() {
        return ResponseEntity.status(404).body(new ErrorHttp("PARTICIPACION_NO_ENCONTRADA", "Participacion no encontrada"));
    }

    @ExceptionHandler(AutorizacionRepeticionVigenteException.class)
    ResponseEntity<ErrorHttp> vigente() {
        return ResponseEntity.status(409).body(new ErrorHttp("AUTORIZACION_VIGENTE",
                "La participacion ya tiene una autorizacion de repeticion vigente"));
    }

    @ExceptionHandler({org.springframework.web.method.annotation.MethodArgumentTypeMismatchException.class,
            org.springframework.http.converter.HttpMessageNotReadableException.class})
    ResponseEntity<ErrorHttp> invalida() {
        return ResponseEntity.badRequest().body(new ErrorHttp("VALIDACION", "Solicitud invalida"));
    }
}
