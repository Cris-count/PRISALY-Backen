package com.prisaly.m5.infrastructure.persistencia;

import java.time.Instant;
import java.util.UUID;

/** POJO de fila: edificio visitado; sin @Entity (mapeo JdbcTemplate). */
public record EdificioIntentoEntity(UUID id, UUID idIntento, String nombre, String ubicacion, Instant fecha) {}
