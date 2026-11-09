package com.prisaly.m4.api;

import java.util.List;
import java.util.UUID;

/** Contrato publicado para que otros módulos conozcan las convocatorias de una sesión. */
public interface ConvocatoriasSesionPort {
    List<UUID> idsPorSesion(UUID sesionId);
}
