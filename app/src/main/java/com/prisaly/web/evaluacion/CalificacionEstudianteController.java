package com.prisaly.web.evaluacion;

import com.prisaly.config.m6.LiberacionCalificacionM6;
import com.prisaly.config.m6.M6LiberacionConfig;
import com.prisaly.m5.presentation.TraductorErroresM5;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * N2 CANDIDATE (DC-BE-CIERRE-DEFINITIVO-87): {@code GET /api/v1/estudiantes/intentos/{idIntento}/calificacion}, the route
 * reserved in CONTRATOS-HTTP-M6-55. Student session (DD-02) only, protected by the {@code /v1/estudiantes/**} chain.
 * Identity and titularity are verified by M5 first, so 401/403/404 are exactly M5's. A grade not yet released and an
 * attempt without a grade are the same 404 {@code CALIFICACION_NO_DISPONIBLE}: an unreleased grade never leaks.
 * Only the released value, scale, character and release instant are returned (no per-question detail: RF-14 feedback
 * is not part of N2). {@code no-store}. Exists only with {@code prisaly.m6.liberacion.enabled=true}.
 */
@RestController
@ConditionalOnProperty(name = M6LiberacionConfig.PROPIEDAD, havingValue = "true")
public class CalificacionEstudianteController {

    /**
     * DC-92 DD-12: + general feedback (once published) and, ONLY with the explicit configuration
     * {@code prisaly.m6.resultado.detalle-por-pregunta=true}, the per-question detail; otherwise the field is absent.
     */
    @com.fasterxml.jackson.annotation.JsonInclude(com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL)
    public record CalificacionEstudianteHttp(UUID idIntento, BigDecimal valor, String escala, String caracter,
            Instant fechaLiberacion, String retroalimentacionGeneral,
            java.util.List<LiberacionCalificacionM6.DetallePregunta> detallePorPregunta) { }

    public record ErrorHttp(String code, String message) { }

    private final LiberacionCalificacionM6 liberacion;
    private final ObjectMapper json;
    private final com.prisaly.m6.application.port.ConsultaRetroalimentacionPort retroalimentacion;
    private final boolean detallePorPregunta;

    public CalificacionEstudianteController(LiberacionCalificacionM6 liberacion, ObjectMapper json,
            com.prisaly.m6.application.port.ConsultaRetroalimentacionPort retroalimentacion,
            @org.springframework.beans.factory.annotation.Value("${prisaly.m6.resultado.detalle-por-pregunta:false}")
            boolean detallePorPregunta) {
        this.liberacion = liberacion;
        this.json = json;
        this.retroalimentacion = retroalimentacion;
        this.detallePorPregunta = detallePorPregunta;
    }

    @GetMapping(value = "/v1/estudiantes/intentos/{idIntento}/calificacion", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> consultar(@PathVariable UUID idIntento) {
        return liberacion.consultarComoEstudiante(idIntento)
                .<ResponseEntity<?>>map(l -> {
                    JsonNode detalle = json.readerFor(JsonNode.class)
                            .with(DeserializationFeature.USE_BIG_DECIMAL_FOR_FLOATS)
                            .readValue(l.calificacion().detalleEvaluacion());
                    return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(new CalificacionEstudianteHttp(
                            idIntento, l.calificacion().valor(), "0-5", detalle.path("caracter").asString("formativa"),
                            l.fechaLiberacion(),
                            liberacion.retroalimentacionGeneral(idIntento, retroalimentacion).orElse(null),
                            detallePorPregunta ? liberacion.detallePorPregunta(idIntento, l.calificacion().idRubrica()) : null));
                })
                .orElseGet(() -> ResponseEntity.status(404).cacheControl(CacheControl.noStore())
                        .body(new ErrorHttp("CALIFICACION_NO_DISPONIBLE", "No hay una calificacion liberada")));
    }

    /** M5 identity/titularity errors keep M5's status and body. */
    @ExceptionHandler(RuntimeException.class)
    ResponseEntity<?> m5(RuntimeException ex) {
        if (ex instanceof MethodArgumentTypeMismatchException) {
            return ResponseEntity.badRequest().body(new ErrorHttp("VALIDATION_ERROR", "Request validation failed"));
        }
        var traducido = TraductorErroresM5.traducir(ex).orElseThrow(() -> ex);
        return ResponseEntity.status(traducido.estado()).cacheControl(CacheControl.noStore()).body(traducido.cuerpo());
    }
}
