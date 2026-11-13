package com.prisaly.m4.infrastructure.adapter;

import com.prisaly.m4.application.port.RelojAcademicoPort;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.Objects;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Reloj académico de M4 leído de PostgreSQL en la conexión de la transacción en curso
 * ({@code MANDATORY}): la marca y la fila que la persiste pertenecen a la misma transacción.
 */
@Repository
public class RelojAcademicoJdbcAdapter implements RelojAcademicoPort {
    private final JdbcTemplate jdbc;

    public RelojAcademicoJdbcAdapter(JdbcTemplate jdbc) {
        this.jdbc = Objects.requireNonNull(jdbc);
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public Instant ahora() {
        OffsetDateTime ahora = jdbc.queryForObject("SELECT clock_timestamp()", OffsetDateTime.class);
        return Objects.requireNonNull(ahora, "PostgreSQL no devolvió clock_timestamp()").toInstant();
    }
}
