package com.prisaly.m5.infrastructure.persistencia;

import java.time.Instant;
import java.util.UUID;

/** POJO de fila: nota de investigación; sin @Entity (mapeo JdbcTemplate). */
public record NotaIntentoEntity(UUID id, UUID idIntento, String titulo, String texto, Instant fecha) {}
