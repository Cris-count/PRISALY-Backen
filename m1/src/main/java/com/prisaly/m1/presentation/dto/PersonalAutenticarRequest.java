package com.prisaly.m1.presentation.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

/** Cuerpo de POST /v1/personal/autenticar. Correo y contraseña en claro solo en el viaje HTTP. */
public record PersonalAutenticarRequest(
        @NotBlank @Email String correo,
        @NotBlank String password
) {
}
