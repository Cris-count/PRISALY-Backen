package com.prisaly.m5.infrastructure.persistencia;

import java.time.Instant;
import java.util.UUID;

/** POJO de fila: pista; sin @Entity (mapeo JdbcTemplate). */
public record PistaIntentoEntity(UUID id, UUID idIntento, String descripcion, Instant fecha) {}
