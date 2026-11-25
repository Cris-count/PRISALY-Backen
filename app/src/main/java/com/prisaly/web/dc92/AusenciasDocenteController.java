package com.prisaly.web.dc92;

import com.prisaly.config.dc92.ParticipacionesDc92Config;
import com.prisaly.config.dc92.ParticipacionesDocenteDc92;
import java.net.URI;
import java.util.List;
import java.util.UUID;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * DC-92 DD-13 (owner teacher, M4 rule):
 * <ul>
 *   <li>{@code POST /api/v1/docente/convocatorias/{id}/ausencia} {@code {estado: AUSENCIA_JUSTIFICADA |
 *       AUSENCIA_NO_JUSTIFICADA, motivo?}} → 201; only from AUSENTE_PENDIENTE_REVISION or a previous classification,
 *       otherwise 409 TRANSICION_NO_VALIDA; never creates a grade;</li>
 *   <li>{@code GET /api/v1/docente/convocatorias/{id}/ausencia} → 200 {@code {"items":[...]}} full history.</li>
 * </ul>
 */
@RestController
@ConditionalOnProperty(name = ParticipacionesDc92Config.PROPIEDAD_DD13, havingValue = "true")
@RequestMapping(value = "/v1/docente/convocatorias/{idConvocatoria}/ausencia", produces = MediaType.APPLICATION_JSON_VALUE)
public class AusenciasDocenteController {

    public record SolicitudClasificacion(String estado, String motivo) { }
    public record Historial(List<ParticipacionesDocenteDc92.Clasificacion> items) { }

    private final ParticipacionesDocenteDc92 participaciones;

    public AusenciasDocenteController(ParticipacionesDocenteDc92 participaciones) {
        this.participaciones = participaciones;
    }

    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<ParticipacionesDocenteDc92.Clasificacion> clasificar(@PathVariable UUID idConvocatoria,
            @RequestBody(required = false) SolicitudClasificacion cuerpo) {
        var c = participaciones.clasificarAusencia(idConvocatoria, cuerpo == null ? null : cuerpo.estado(),
                cuerpo == null ? null : cuerpo.motivo());
        return ResponseEntity.created(URI.create("/api/v1/docente/convocatorias/" + idConvocatoria + "/ausencia")).body(c);
    }

    @GetMapping
    public ResponseEntity<Historial> historial(@PathVariable UUID idConvocatoria) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .body(new Historial(participaciones.historialAusencia(idConvocatoria)));
    }
}
