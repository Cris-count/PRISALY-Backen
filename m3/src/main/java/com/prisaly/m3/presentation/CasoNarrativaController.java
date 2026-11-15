package com.prisaly.m3.presentation;

import com.fasterxml.jackson.annotation.JsonRawValue;
import com.prisaly.m3.application.port.GuionNarrativoRepositorio.GuionGuardado;
import com.prisaly.m3.application.service.GuionNarrativoService;

import java.time.Instant;
import java.util.UUID;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Autoría docente del guion narrativo (DC-BE-SIM-INCREMENTO-A-15). <b>No publicado por defecto</b>: solo se registra
 * con {@code prisaly.m3.narrativa.publicacion.enabled=true} ({@code false} en {@code application.yml}). Rutas de
 * personal (Bearer de M1); la autoría y el bloqueo los decide {@link GuionNarrativoService}.
 */
@RestController
// Same switch as M3NarrativaConfig.PROPIEDAD (presentation must not import infrastructure).
@ConditionalOnProperty(name = "prisaly.m3.narrativa.publicacion.enabled", havingValue = "true")
@RequestMapping(value = "/v1/casos/{idCaso}/narrativa", produces = MediaType.APPLICATION_JSON_VALUE)
public class CasoNarrativaController {
    private final GuionNarrativoService guiones;

    public CasoNarrativaController(GuionNarrativoService guiones) {
        this.guiones = guiones;
    }

    /** Versión de guion vista por el docente; {@code definicion} es el documento JSON tal como está persistido. */
    public record GuionDto(UUID idGuion, int version, String estado, String hashDefinicion, Instant fechaActualizacion,
            Instant fechaPublicacion, @JsonRawValue String definicion) {
        static GuionDto de(GuionGuardado g) {
            return new GuionDto(g.idGuion(), g.version(), g.estado(), g.hash(), g.fechaActualizacion(),
                    g.fechaPublicacion().orElse(null), g.definicionJson());
        }
    }

    public record NarrativaDocenteDto(UUID idCaso, GuionDto borrador, GuionDto publicado) { }

    @GetMapping
    public ResponseEntity<NarrativaDocenteDto> consultar(@PathVariable("idCaso") String idCaso) {
        var vista = guiones.consultar(IdentificadorCanonico.uuid(idCaso));
        return ok(new NarrativaDocenteDto(vista.idCaso(), vista.borrador().map(GuionDto::de).orElse(null),
                vista.publicado().map(GuionDto::de).orElse(null)));
    }

    /** El cuerpo es el documento {@code prisaly-guion-1} completo; reemplaza el borrador vigente. */
    @PutMapping(consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<GuionDto> guardarBorrador(@PathVariable("idCaso") String idCaso, @RequestBody String documento) {
        return ok(GuionDto.de(guiones.guardarBorrador(IdentificadorCanonico.uuid(idCaso), documento)));
    }

    @PostMapping("/publicar")
    public ResponseEntity<GuionDto> publicar(@PathVariable("idCaso") String idCaso) {
        return ok(GuionDto.de(guiones.publicar(IdentificadorCanonico.uuid(idCaso))));
    }

    private static <T> ResponseEntity<T> ok(T cuerpo) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(cuerpo);
    }
}
