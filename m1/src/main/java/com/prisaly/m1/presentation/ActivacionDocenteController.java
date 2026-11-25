package com.prisaly.m1.presentation;

import com.prisaly.m1.application.service.GestionActivacionDocenteService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.UUID;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/**
 * D-05 (DC-BE-D05-AUTORIZACION-QA-77, candidate, QA only). OFF by default: without
 * {@code prisaly.m1.activacion-docente.enabled=true} this controller does not exist (404).
 * <ul>
 *   <li>{@code POST /v1/admin/docentes/{id}/activacion} (administrator): issues or re-issues the invitation; 202, no
 *       body. The token is only mailed, never returned.</li>
 *   <li>{@code DELETE /v1/admin/docentes/{id}/activacion} (administrator): revokes the live invitation; 204.</li>
 *   <li>{@code POST /v1/personal/activar} (public, rate limited): {@code {token, password}}; 204. Every invalid,
 *       expired, consumed or revoked token answers the same 400 {@code ACTIVATION_INVALID}.</li>
 * </ul>
 * Responses carry {@code Cache-Control: no-store}. The origin used for limits is the TCP peer (as DM1-06).
 */
@RestController
@ConditionalOnProperty(name = "prisaly.m1.activacion-docente.enabled", havingValue = "true")
public class ActivacionDocenteController {
    private final GestionActivacionDocenteService servicio;

    public ActivacionDocenteController(GestionActivacionDocenteService servicio) {
        this.servicio = servicio;
    }

    /** Sizes are generous upper bounds; the exact token format and DM1-08 are enforced by the service. */
    public record ActivacionRequest(@NotNull @Size(max = 128) String token, @NotNull @Size(max = 1024) String password) {
        @Override
        public String toString() {
            return "ActivacionRequest[token=<oculto>, password=<oculto>]";
        }
    }

    @PostMapping("/v1/admin/docentes/{idUsuario}/activacion")
    public ResponseEntity<Void> emitir(@PathVariable UUID idUsuario) {
        servicio.emitir(idUsuario);
        return ResponseEntity.accepted().cacheControl(CacheControl.noStore()).build();
    }

    @DeleteMapping("/v1/admin/docentes/{idUsuario}/activacion")
    public ResponseEntity<Void> revocar(@PathVariable UUID idUsuario) {
        servicio.revocar(idUsuario);
        return ResponseEntity.noContent().cacheControl(CacheControl.noStore()).build();
    }

    @PostMapping("/v1/personal/activar")
    public ResponseEntity<Void> activar(@Valid @RequestBody ActivacionRequest request, HttpServletRequest http) {
        servicio.activar(request.token(), request.password(), http.getRemoteAddr());
        return ResponseEntity.noContent().cacheControl(CacheControl.noStore()).build();
    }
}
