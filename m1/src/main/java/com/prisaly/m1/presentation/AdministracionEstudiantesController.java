package com.prisaly.m1.presentation;

import com.prisaly.m1.application.port.EstudianteRegistroPort.EstudianteRegistrado;
import com.prisaly.m1.application.service.AltaEstudianteService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.List;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * P-78 (DC-BE-D78-1-AUTORIZACION-CONTROLADA-79, candidate, QA only): administrative student registration. OFF by
 * default: without {@code prisaly.m1.alta-estudiantes.enabled=true} this controller does not exist (404).
 * <ul>
 *   <li>{@code POST /v1/admin/estudiantes} {@code {nombre, correo}} → 201
 *       {@code {idUsuario, idEstudiante, nombre, correo, estado}}. No credential is created or returned.</li>
 *   <li>{@code GET /v1/admin/estudiantes?correo=} → 200 {@code {items:[...]}}, exact case-insensitive match.</li>
 * </ul>
 * Both require an active administrator; responses carry {@code Cache-Control: no-store}.
 */
@RestController
@RequestMapping("/v1/admin/estudiantes")
@ConditionalOnProperty(name = "prisaly.m1.alta-estudiantes.enabled", havingValue = "true")
public class AdministracionEstudiantesController {
    private final AltaEstudianteService servicio;

    public AdministracionEstudiantesController(AltaEstudianteService servicio) {
        this.servicio = servicio;
    }

    /** Same shape and limits as {@code DocenteAltaRequest} (ARQ-008.3): nombre and correo only. */
    public record EstudianteAltaRequest(@NotBlank @Size(max = 150) String nombre,
            @NotBlank @Email @Size(max = 255) String correo) { }

    public record Listado<T>(List<T> items) { }

    @PostMapping
    public ResponseEntity<EstudianteRegistrado> alta(@Valid @RequestBody EstudianteAltaRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).cacheControl(CacheControl.noStore())
                .body(servicio.alta(request.nombre().trim(), request.correo()));
    }

    @GetMapping
    public ResponseEntity<Listado<EstudianteRegistrado>> buscar(@RequestParam("correo") String correo) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .body(new Listado<>(servicio.buscarPorCorreo(correo)));
    }
}
