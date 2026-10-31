package com.prisaly.m4.presentation.dto;

import jakarta.validation.constraints.*;

import java.time.Instant;

public record ActualizarSesionRequest(
        @NotBlank @Size(max = 150) String titulo,
        @com.fasterxml.jackson.annotation.JsonAlias("descripcion") String mensajePersonalizado,
        @NotNull Instant fechaInicio,
        @NotNull Instant fechaFin) {}
