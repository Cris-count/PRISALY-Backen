package com.prisaly.m4.infrastructure.adapter;

import com.prisaly.m4.api.ConsultaProgramacionNoDisponibleException;
import com.prisaly.m4.api.ResponsableConvocatoriaPort;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Lectura de la programación de una convocatoria sobre tablas oficiales de M4.
 *
 * <p>No es un componente de Spring: se ensambla explícitamente (A4/RF-25, repetición no habilitada).
 */
public class ResponsableConvocatoriaJdbcAdapter implements ResponsableConvocatoriaPort {
    private static final String SQL = """
            SELECT s."idSesion", s."idCaso", s."idDocenteProgramador"
            FROM public."Convocatoria" c
            JOIN public."Sesion" s ON s."idSesion" = c."idSesion"
            WHERE c."idConvocatoria" = ?
            """;

    private final JdbcTemplate jdbc;

    public ResponsableConvocatoriaJdbcAdapter(JdbcTemplate jdbc) {
        this.jdbc = Objects.requireNonNull(jdbc);
    }

    @Override
    public Optional<ProgramacionConvocatoria> consultar(UUID convocatoriaId) {
        Objects.requireNonNull(convocatoriaId, "La convocatoria es obligatoria");
        try {
            List<ProgramacionConvocatoria> filas = jdbc.query(SQL, (rs, n) -> new ProgramacionConvocatoria(
                    rs.getObject(1, UUID.class), rs.getObject(2, UUID.class), rs.getObject(3, UUID.class)),
                    convocatoriaId);
            return filas.stream().findFirst();
        } catch (DataAccessException ex) {
            throw new ConsultaProgramacionNoDisponibleException(ex);
        }
    }
}
