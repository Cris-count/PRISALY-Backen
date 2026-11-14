package com.prisaly.m1.presentation;

import com.prisaly.m1.application.service.AdministracionIdentidadService;
import com.prisaly.m1.domain.model.AsignacionPermiso;
import com.prisaly.m1.domain.model.Permiso;
import com.prisaly.m1.domain.model.Usuario;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * DM1-04 (DC-M1-04): administrative API authorized by Dirección Central, following ARQ-008.3 paths under /v1.
 * Role assignment (PUT /admin/usuarios/{id}/roles/{idRol}) is NOT implemented: incompatible with BD-005.2's single role.
 * Revocation (DELETE) is not in ARQ-008.3; it was authorized by DC-M1-04.
 */
@RestController
@RequestMapping("/v1/admin")
public class AdministracionController {
    private final AdministracionIdentidadService servicio;

    public AdministracionController(AdministracionIdentidadService servicio) {
        this.servicio = servicio;
    }

    /** ARQ-008.3 DocenteAltaRequest: nombre and correo only (additionalProperties: false). */
    public record DocenteAltaRequest(@NotBlank @Size(max = 150) String nombre,
            @NotBlank @Email @Size(max = 255) String correo) { }

    public record UsuarioDocente(UUID idUsuario, String nombre, String correo, String estado) { }

    public record RolResumen(Integer idRol, String nombre) { }

    /** ARQ-008.3 PermisoResumen plus {@code codigo} (conciliation item C-ADM-02: the code is the operational key). */
    public record PermisoResumen(Integer idPermiso, String codigo, String nombre) {
        static PermisoResumen de(Permiso p) {
            return new PermisoResumen(p.getIdPermiso(), p.getCodigo(), p.getNombre());
        }
    }

    public record Listado<T>(List<T> items) { }

    public record AsignacionPermisoUsuario(UUID idUsuario, Integer idPermiso, boolean habilitado) { }

    /** Crea un docente activo. La contraseña inicial la genera el servicio, no el cuerpo. */
    @PostMapping("/docentes")
    public ResponseEntity<UsuarioDocente> altaDocente(@Valid @RequestBody DocenteAltaRequest request) {
        Usuario creado = servicio.altaDocente(request.nombre().trim(), request.correo());
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(new UsuarioDocente(creado.getIdUsuario(), creado.getNombre(), creado.getCorreo(), creado.getEstado()));
    }

    /** Catálogo de roles. No asigna ni cambia el rol de nadie. */
    @GetMapping("/roles")
    public Listado<RolResumen> roles() {
        return new Listado<>(servicio.roles().stream().map(r -> new RolResumen(r.getIdRol(), r.getNombre())).toList());
    }

    /** Catálogo de permisos, incluido el código que luego se asigna. */
    @GetMapping("/permisos")
    public Listado<PermisoResumen> permisos() {
        return new Listado<>(servicio.permisos().stream().map(PermisoResumen::de).toList());
    }

    /** Permisos habilitados de un usuario. Exige administrador. */
    @GetMapping("/usuarios/{idUsuario}/permisos")
    public Listado<PermisoResumen> permisosDeUsuario(@PathVariable UUID idUsuario) {
        return new Listado<>(servicio.permisosDeUsuario(idUsuario).stream().map(PermisoResumen::de).toList());
    }

    /** Habilita un permiso ya existente. No cambia el rol. */
    @PutMapping("/usuarios/{idUsuario}/permisos/{idPermiso}")
    public AsignacionPermisoUsuario asignar(@PathVariable UUID idUsuario, @PathVariable Integer idPermiso) {
        AsignacionPermiso a = servicio.asignar(idUsuario, idPermiso);
        return new AsignacionPermisoUsuario(a.getIdUsuario(), a.getIdPermiso(), a.isHabilitado());
    }

    /** Quita un permiso habilitado. Responde 204 sin cuerpo. */
    @DeleteMapping("/usuarios/{idUsuario}/permisos/{idPermiso}")
    public ResponseEntity<Void> revocar(@PathVariable UUID idUsuario, @PathVariable Integer idPermiso) {
        servicio.revocar(idUsuario, idPermiso);
        return ResponseEntity.noContent().build();
    }
}
