package com.prisaly.web.dc92;

import com.prisaly.config.dc92.RetiroConvocatoriaRf21;
import com.prisaly.config.dc92.RetiroRf21Config;
import java.net.URI;
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
 * DC-92 RF-21 (owner teacher, M4 rule):
 * <ul>
 *   <li>{@code POST /api/v1/docente/convocatorias/{id}/retiro} {@code {motivo?, resolucionIntentoEnCurso?}} → 201;
 *       409 YA_RETIRADO, 409 INTENTO_EN_CURSO_REQUIERE_RESOLUCION, 400 VALIDACION;</li>
 *   <li>{@code GET /api/v1/docente/convocatorias/{id}/retiro} → 200, or 404 RETIRO_NO_REGISTRADO.</li>
 * </ul>
 */
@RestController
@ConditionalOnProperty(name = RetiroRf21Config.PROPIEDAD, havingValue = "true")
@RequestMapping(value = "/v1/docente/convocatorias/{idConvocatoria}/retiro", produces = MediaType.APPLICATION_JSON_VALUE)
public class RetiroConvocatoriaController {

    public record SolicitudRetiro(String motivo, String resolucionIntentoEnCurso) { }

    private final RetiroConvocatoriaRf21 retiro;

    public RetiroConvocatoriaController(RetiroConvocatoriaRf21 retiro) {
        this.retiro = retiro;
    }

    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<RetiroConvocatoriaRf21.Retiro> retirar(@PathVariable UUID idConvocatoria,
            @RequestBody(required = false) SolicitudRetiro cuerpo) {
        var r = retiro.retirar(idConvocatoria, cuerpo == null ? null : cuerpo.motivo(),
                cuerpo == null ? null : cuerpo.resolucionIntentoEnCurso());
        return ResponseEntity.created(URI.create("/api/v1/docente/convocatorias/" + idConvocatoria + "/retiro")).body(r);
    }

    @GetMapping
    public ResponseEntity<?> consultar(@PathVariable UUID idConvocatoria) {
        return retiro.consultar(idConvocatoria)
                .<ResponseEntity<?>>map(r -> ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(r))
                .orElseGet(() -> ResponseEntity.status(404).body(new ErroresDc92.ErrorHttp("RETIRO_NO_REGISTRADO",
                        "La convocatoria no tiene retiro registrado")));
    }
}
