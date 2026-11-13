package com.prisaly.m3.infrastructure.persistence.jdbc;

import com.prisaly.m3.api.AutoriaCasoNoDisponibleException;
import com.prisaly.m3.api.AutoriaCasoPort;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Lectura del creador del caso sobre la tabla propia de M3. No es un componente de Spring: se ensambla
 * explícitamente junto con la autorización de repetición, que sigue sin habilitarse (PF-08).
 */
public class AutoriaCasoJdbcAdapter implements AutoriaCasoPort {

    private final JdbcTemplate jdbc;

    public AutoriaCasoJdbcAdapter(JdbcTemplate jdbc) {
        this.jdbc = Objects.requireNonNull(jdbc);
    }

    @Override
    public Optional<UUID> docenteCreador(UUID idCaso) {
        Objects.requireNonNull(idCaso, "El caso es obligatorio");
        try {
            List<UUID> filas = jdbc.queryForList(
                    "SELECT \"idDocente\" FROM public.\"Caso\" WHERE \"idCaso\" = ?", UUID.class, idCaso);
            return filas.stream().findFirst();
        } catch (DataAccessException ex) {
            throw new AutoriaCasoNoDisponibleException(ex);
        }
    }
}
