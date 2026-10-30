package com.prisaly.m1.presentation.dto;

import java.util.Set;
import java.util.UUID;

public record PersonalAutenticarResponse(
        String accessToken,
        String tokenType,
        UUID userId,
        String correo,
        String nombre,
        String rol,
        Set<String> permisos,
        String authPrototype
) {
}
