package com.prisaly.web.evaluacion;

import com.prisaly.config.m6.LiberacionCalificacionM6;
import com.prisaly.config.m6.M6LiberacionConfig;
import java.math.BigDecimal;
import java.net.URI;
import java.time.Instant;
import java.util.UUID;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * N2 CANDIDATE (DC-BE-CIERRE-DEFINITIVO-87): {@code POST /api/v1/evaluacion/intentos/{idIntento}/liberacion}, the route
 * reserved in CONTRATOS-HTTP-M6-55. Personnel Bearer only (the student cookie does not authenticate here). Owner
 * teacher only (403 {@code ACCESO_DENEGADO}); 404 {@code CALIFICACION_NO_ENCONTRADA} without a grade. First release 201,
 * any repeated call 200 with the SAME author and instant (single, irreversible release). Errors use the M6 mapping of
 * {@link EvaluacionDocenteExceptionHandler}. Exists only with {@code prisaly.m6.liberacion.enabled=true}.
 */
@RestController
@ConditionalOnProperty(name = M6LiberacionConfig.PROPIEDAD, havingValue = "true")
@RequestMapping(value = "/v1/evaluacion/intentos/{idIntento}/liberacion", produces = MediaType.APPLICATION_JSON_VALUE)
public class LiberacionCalificacionController {

    public record LiberacionHttp(UUID idCalificacion, UUID idIntento, BigDecimal valor, String escala, boolean liberada,
            UUID idDocenteLiberador, Instant fechaLiberacion) { }

    private final LiberacionCalificacionM6 liberacion;

    public LiberacionCalificacionController(LiberacionCalificacionM6 liberacion) {
        this.liberacion = liberacion;
    }

    @PostMapping
    public ResponseEntity<LiberacionHttp> liberar(@PathVariable UUID idIntento) {
        var l = liberacion.liberar(idIntento);
        var cuerpo = new LiberacionHttp(l.calificacion().idCalificacion(), l.calificacion().idIntento(),
                l.calificacion().valor(), "0-5", true, l.idDocente(), l.fechaLiberacion());
        return l.nueva()
                ? ResponseEntity.created(URI.create("/api/v1/evaluacion/intentos/" + idIntento)).body(cuerpo)
                : ResponseEntity.ok(cuerpo);
    }
}
