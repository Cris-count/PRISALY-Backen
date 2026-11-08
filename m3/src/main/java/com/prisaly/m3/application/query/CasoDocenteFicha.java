package com.prisaly.m3.application.query;

import com.prisaly.m3.domain.model.CasoEstado;
import java.util.UUID;

/** Minimal authorized projection; never carries evaluable content or ownership data. */
public record CasoDocenteFicha(UUID idCaso, String titulo, int tiempoEstimadoSegundos, CasoEstado estado) { }
