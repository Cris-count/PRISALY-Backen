package com.prisaly.m4.presentation.controller;

import com.prisaly.m4.application.service.SesionAcademicaService;
import com.prisaly.m4.api.ReglaNegocioException;
import com.prisaly.m4.presentation.dto.ActualizarSesionRequest;
import com.prisaly.m4.presentation.dto.CambiarEstadoRequest;
import com.prisaly.m4.presentation.dto.CrearSesionRequest;
import com.prisaly.m4.presentation.dto.SesionResponse;

import jakarta.validation.Valid;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/**
 * Controlador REST de sesiones. Traduce HTTP al servicio de aplicación y no aplica el patrón State:
 * crear, editar y cambiar estado delegan en {@link com.prisaly.m4.application.service.SesionAcademicaService}.
 * El docente sale del token; la cabecera X-Docente-Id no sustituye esa identidad.
 * Las convocatorias viven en {@link ConvocatoriaController}.
 * La ruta pública queda {@code /api/m4/sesiones} por el context-path.
 */
@RestController
@RequestMapping("/m4/sesiones")
public class SesionController {

    private final SesionAcademicaService service;

    public SesionController(SesionAcademicaService service) {
        this.service = service;
    }

    /** Crea una sesión SinIniciar sobre un caso publicado del docente autenticado. */
    @PostMapping
    public ResponseEntity<SesionResponse> crearSesion(
            @RequestHeader(value = "X-Docente-Id", required = false) UUID docenteId,
            @RequestBody @Valid CrearSesionRequest request) {

        var sesion =
                service.crearSesion(
                        service.resolverDocenteAutenticado(docenteId),
                        request.casoId(),
                        request.titulo(),
                        request.mensajePersonalizado(),
                        request.fechaInicio(),
                        request.fechaFin(),
                        request.tiempoMaximoParticipacionSegundos(),
                        request.lugarFisico());

        return ResponseEntity.status(HttpStatus.CREATED).body(SesionResponse.from(sesion));
    }

    /** Devuelve una sesión si el docente autenticado es su dueño. */
    @GetMapping("/{sesionId}")
    public ResponseEntity<SesionResponse> consultarSesion(@PathVariable UUID sesionId) {

        var sesion = service.consultarSesionAutorizada(sesionId);
        return ResponseEntity.ok(SesionResponse.from(sesion));
    }

    /** Lista las sesiones del docente autenticado. */
    @GetMapping
    public ResponseEntity<List<SesionResponse>> listarSesiones(
            @RequestParam(required = false) UUID docenteId) {

        var sesiones =
                service.listarSesionesAutorizadas(docenteId).stream()
                        .map(SesionResponse::from)
                        .toList();
        return ResponseEntity.ok(sesiones);
    }

    /** Edita título, mensaje, fechas y lugar mientras el estado lo permite. */
    @PutMapping("/{sesionId}")
    public ResponseEntity<SesionResponse> actualizarSesion(
            @RequestHeader(value = "X-Docente-Id", required = false) UUID docenteId,
            @PathVariable UUID sesionId,
            @RequestBody @Valid ActualizarSesionRequest request) {

        var sesion =
                service.actualizarSesion(
                        service.resolverDocenteAutenticado(docenteId), sesionId,
                        request.titulo(), request.mensajePersonalizado(),
                        request.fechaInicio(), request.fechaFin(),
                        request.lugarFisico());

        return ResponseEntity.ok(SesionResponse.from(sesion));
    }

    /** Pasa la sesión a EnCurso o Finalizado. EnCurso ya no admite convocatorias nuevas. */
    @PatchMapping("/{sesionId}/estado")
    public ResponseEntity<SesionResponse> cambiarEstado(
            @RequestHeader(value = "X-Docente-Id", required = false) UUID docenteId,
            @PathVariable UUID sesionId,
            @RequestBody @Valid CambiarEstadoRequest request) {

        var sesion =
                service.cambiarEstado(
                        service.resolverDocenteAutenticado(docenteId),
                        sesionId,
                        request.nuevoEstado());
        return ResponseEntity.ok(SesionResponse.from(sesion));
    }

    /** Ruta retirada. Responde 410 y no autoriza al estudiante. */
    @GetMapping("/{sesionId}/acceso/{estudianteId}")
    public ResponseEntity<Boolean> verificarAcceso(
            @PathVariable UUID sesionId, @PathVariable UUID estudianteId) {

        throw new ReglaNegocioException(
                "M4-ACCESO-LEGADO-RETIRADO",
                "Esta ruta no autoriza acceso estudiantil; use el contrato interno y la política"
                        + " aprobada");
    }
}
