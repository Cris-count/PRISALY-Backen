package com.prisaly.web.evaluacion;

import com.prisaly.config.m6.EvaluacionDocenteM6;
import com.prisaly.m6.domain.model.Calificacion;
import java.math.BigDecimal;
import java.net.URI;
import java.time.Instant;
import java.util.UUID;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * CONTRATOS-HTTP-M6-55 §1 and §2: explicit evaluation of an attempt by the owning teacher and the teacher's query.
 * Exists only with {@code prisaly.m6.evaluacion.enabled=true}; otherwise both routes are 404. Personnel Bearer
 * authentication comes from {@code HttpApiConfig} ({@code anyRequest().authenticated()}).
 */
@RestController
@ConditionalOnProperty(prefix = "prisaly.m6.evaluacion", name = "enabled", havingValue = "true")
@RequestMapping(value = "/v1/evaluacion/intentos/{idIntento}", produces = MediaType.APPLICATION_JSON_VALUE)
public class EvaluacionDocenteController {

    /** Request body of §1. */
    public record SolicitudEvaluacionHttp(UUID idRubrica) { }

    /** Response body of §1 and §2. N2 is not implemented (B2), so {@code liberada} is always false. */
    public record CalificacionHttp(UUID idCalificacion, UUID idIntento, UUID idRubrica, BigDecimal valor,
            String escala, String caracter, String versionPolitica, Instant fechaCalculo, boolean liberada,
            JsonNode detalle) { }

    private final EvaluacionDocenteM6 evaluacion;
    private final ObjectMapper json;

    public EvaluacionDocenteController(EvaluacionDocenteM6 evaluacion, ObjectMapper json) {
        this.evaluacion = evaluacion;
        this.json = json;
    }

    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<CalificacionHttp> evaluar(@PathVariable UUID idIntento,
            @RequestBody SolicitudEvaluacionHttp cuerpo) {
        if (cuerpo == null || cuerpo.idRubrica() == null) {
            throw new SolicitudEvaluacionInvalidaException();
        }
        Calificacion calificacion = evaluacion.evaluar(idIntento, cuerpo.idRubrica());
        return ResponseEntity.created(URI.create("/api/v1/evaluacion/intentos/" + idIntento)).body(http(calificacion));
    }

    @GetMapping
    public ResponseEntity<?> consultar(@PathVariable UUID idIntento) {
        return evaluacion.consultar(idIntento)
                .<ResponseEntity<?>>map(c -> ResponseEntity.ok(http(c)))
                .orElseGet(() -> ResponseEntity.status(404).body(new ErrorEvaluacionHttp(
                        "CALIFICACION_NO_ENCONTRADA", "El intento no tiene calificacion", null)));
    }

    private CalificacionHttp http(Calificacion c) {
        // DC-57: decimals are read as BigDecimal so weights keep their persisted scale (10.00, not 10.0).
        JsonNode detalle = json.readerFor(JsonNode.class)
                .with(DeserializationFeature.USE_BIG_DECIMAL_FOR_FLOATS).readValue(c.detalleEvaluacion());
        String caracter = detalle.path("caracter").asString("formativa");
        return new CalificacionHttp(c.idCalificacion(), c.idIntento(), c.idRubrica(), c.valor(), "0-5", caracter,
                c.versionPolitica(), c.fechaCalculo(), false, detalle);
    }

    /** Missing {@code idRubrica}. */
    static final class SolicitudEvaluacionInvalidaException extends RuntimeException {
        private static final long serialVersionUID = 1L;
        SolicitudEvaluacionInvalidaException() {
            super("idRubrica es obligatorio");
        }
    }
}
