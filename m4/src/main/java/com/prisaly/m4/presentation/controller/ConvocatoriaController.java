package com.prisaly.m4.presentation.controller;

import com.prisaly.m4.application.service.SesionAcademicaService;
import com.prisaly.m4.presentation.dto.ConvocarEstudianteRequest;
import com.prisaly.m4.presentation.dto.ConvocatoriaResponse;

import jakarta.validation.Valid;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/** Gestiona convocatorias conservando las rutas y los controles de propiedad existentes. */
@RestController
@RequestMapping("/m4/sesiones/{sesionId}/convocatorias")
public class ConvocatoriaController {
    private final SesionAcademicaService service;

    public ConvocatoriaController(SesionAcademicaService service) {
        this.service = service;
    }

    @PostMapping
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

    @GetMapping
    public ResponseEntity<List<ConvocatoriaResponse>> consultarConvocatorias(
            @PathVariable UUID sesionId) {
        return ResponseEntity.ok(
                service.consultarConvocatoriasAutorizadas(sesionId).stream()
                        .map(ConvocatoriaResponse::from)
                        .toList());
    }
}
