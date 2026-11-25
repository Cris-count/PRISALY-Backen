package com.prisaly.web.dc92;

import com.prisaly.config.dc92.ParticipacionesDc92Config;
import com.prisaly.config.dc92.ParticipacionesDocenteDc92;
import com.prisaly.config.dc92.VistaParticipacionDc92;
import java.util.List;
import java.util.UUID;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

/**
 * DC-92 DD-09: {@code GET /api/v1/docente/sesiones/{idSesion}/participaciones} → {@code {"items":[...]}} with the DC-90
 * fields (student, session and convocation, entry date, start and end, participation state, number of attempts,
 * evaluation state, PUBLISHED grade only). Owner teacher only (M4 rule). {@code no-store}.
 */
@RestController
@ConditionalOnProperty(name = ParticipacionesDc92Config.PROPIEDAD_DD09, havingValue = "true")
public class ParticipacionesDocenteController {

    public record Listado(List<VistaParticipacionDc92.Fila> items) { }

    private final ParticipacionesDocenteDc92 participaciones;

    public ParticipacionesDocenteController(ParticipacionesDocenteDc92 participaciones) {
        this.participaciones = participaciones;
    }

    @GetMapping(value = "/v1/docente/sesiones/{idSesion}/participaciones", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<Listado> listar(@PathVariable UUID idSesion) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .body(new Listado(participaciones.participaciones(idSesion)));
    }
}
