package com.prisaly.m4.presentation.controller;

import com.prisaly.m4.application.service.SesionAcademicaService;
import com.prisaly.m4.domain.rule.ReglaNegocioException;
import com.prisaly.m4.presentation.dto.ActualizarSesionRequest;
import com.prisaly.m4.presentation.dto.CambiarEstadoRequest;
import com.prisaly.m4.presentation.dto.ConvocarEstudianteRequest;
import com.prisaly.m4.presentation.dto.ConvocatoriaResponse;
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
 * Controlador REST de M4 – Programación Académica y Convocatorias.
 *
 * <p>Los endpoints usan el prefijo {@code /m4/sesiones} que se combina con el context-path {@code
 * /api} configurado en application.yml, resultando en rutas como {@code /api/m4/sesiones}.
 *
 * <p>NOTA: Los nombres de rutas son propuestos. Si ARQ-008.3 define rutas diferentes, deben
 * ajustarse.
 */
@RestController
@RequestMapping("/m4/sesiones")
public class SesionController {

    private final SesionAcademicaService service;

    public SesionController(SesionAcademicaService service) {
        this.service = service;
    }

    // ─── Crear sesión ─────────────────────────────────────────

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
                        request.tiempoMaximoParticipacionSegundos());

        return ResponseEntity.status(HttpStatus.CREATED).body(SesionResponse.from(sesion));
    }

    // ─── Consultar sesión ─────────────────────────────────────

    @GetMapping("/{sesionId}")
    public ResponseEntity<SesionResponse> consultarSesion(@PathVariable UUID sesionId) {

        var sesion = service.consultarSesionAutorizada(sesionId);
        return ResponseEntity.ok(SesionResponse.from(sesion));
    }

    // ─── Listar sesiones ──────────────────────────────────────

    @GetMapping
    public ResponseEntity<List<SesionResponse>> listarSesiones(
            @RequestParam(required = false) UUID docenteId) {

        var sesiones =
                service.listarSesionesAutorizadas(docenteId).stream()
                        .map(SesionResponse::from)
                        .toList();
        return ResponseEntity.ok(sesiones);
    }

    // ─── Actualizar sesión ────────────────────────────────────

    @PutMapping("/{sesionId}")
    public ResponseEntity<SesionResponse> actualizarSesion(
            @RequestHeader(value = "X-Docente-Id", required = false) UUID docenteId,
            @PathVariable UUID sesionId,
            @RequestBody @Valid ActualizarSesionRequest request) {

        var sesion =
                service.actualizarSesion(
                        service.resolverDocenteAutenticado(docenteId), sesionId,
                        request.titulo(), request.mensajePersonalizado(),
                        request.fechaInicio(), request.fechaFin());

        return ResponseEntity.ok(SesionResponse.from(sesion));
    }

    // ─── Cambiar estado ───────────────────────────────────────

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

    // ─── Convocar estudiante ──────────────────────────────────

    @PostMapping("/{sesionId}/convocatorias")
    public ResponseEntity<ConvocatoriaResponse> convocarEstudiante(
            @RequestHeader(value = "X-Docente-Id", required = false) UUID docenteId,
            @PathVariable UUID sesionId,
            @RequestBody @Valid ConvocarEstudianteRequest request) {

        var convocatoria =
                service.convocarEstudiante(
                        service.resolverDocenteAutenticado(docenteId),
                        sesionId,
                        request.estudianteId());

        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ConvocatoriaResponse.from(convocatoria));
    }

    // ─── Consultar convocatorias ──────────────────────────────

    @GetMapping("/{sesionId}/convocatorias")
    public ResponseEntity<List<ConvocatoriaResponse>> consultarConvocatorias(
            @PathVariable UUID sesionId) {

        var convocatorias =
                service.consultarConvocatoriasAutorizadas(sesionId).stream()
                        .map(ConvocatoriaResponse::from)
                        .toList();
        return ResponseEntity.ok(convocatorias);
    }

    // ─── Verificar acceso (expuesto para M5) ──────────────────

    @GetMapping("/{sesionId}/acceso/{estudianteId}")
    public ResponseEntity<Boolean> verificarAcceso(
            @PathVariable UUID sesionId, @PathVariable UUID estudianteId) {

        throw new ReglaNegocioException(
                "M4-ACCESO-LEGADO-RETIRADO",
                "Esta ruta no autoriza acceso estudiantil; use el contrato interno y la política"
                    + " aprobada");
    }
}
