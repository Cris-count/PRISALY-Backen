package com.prisaly.m3.presentation.dto;

import com.prisaly.m3.application.query.CasoDocenteFicha;
import java.util.UUID;

/** Explicit teacher fields only; no academic answers, owner or internal metadata. */
public record CasoDocenteConsultaResponse(UUID idCaso, String titulo, int tiempoEstimadoSegundos, String estado) {
    public static CasoDocenteConsultaResponse from(CasoDocenteFicha ficha) {
        return new CasoDocenteConsultaResponse(ficha.idCaso(), ficha.titulo(),
                ficha.tiempoEstimadoSegundos(), ficha.estado().name());
    }
}
