package com.prisaly.m5.infrastructure.persistencia;

import java.time.Instant;
import java.util.UUID;

/** POJO de fila: zona explorada; sin @Entity (mapeo JdbcTemplate). */
public record ZonaIntentoEntity(UUID id, UUID idIntento, String nombre, String descripcion, Instant fecha) {}
