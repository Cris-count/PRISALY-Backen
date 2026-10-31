package com.prisaly.m1.presentation.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

public record PersonalAutenticarRequest(
        @NotBlank @Email String correo,
        @NotBlank String password
) {
}
