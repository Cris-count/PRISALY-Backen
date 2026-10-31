package com.prisaly.m4.infrastructure.adapter;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

import com.prisaly.m4.api.ConsultaProgramacionNoDisponibleException;
import com.prisaly.m4.api.ContextoProgramacion;
import com.prisaly.m4.api.ProgramacionParticipacionPort;

/**
 * Lectura atómica mediante un único SELECT de tablas oficiales. No escribe,
 * no lee tablas de M5 y no utiliza la propuesta V010. Activación explícita
 * en M4ProgramacionConsultaAutoConfiguration; apagado por defecto.
 */
@Transactional(readOnly = true)
public class ProgramacionParticipacionJdbcAdapter implements ProgramacionParticipacionPort {
    private static final String SELECT = """
            SELECT c."idConvocatoria" AS convocatoria_id, c."idEstudiante" AS estudiante_id,
                   s."idSesion" AS sesion_id, s."idCaso" AS caso_id,
                   s."estado" AS estado_sesion, c."estadoAutorizacion" AS estado_convocatoria,
                   c."fechaRetiro" AS fecha_retiro, k."estado" AS estado_caso, k."activo" AS caso_activo,
                   s."inicioPrevisto" AS inicio_previsto, s."finPrevisto" AS fin_previsto,
                   s."fechaInicioReal" AS inicio_real, s."fechaFinReal" AS fin_real,
                   s."tiempoMaximoParticipacionSegundos" AS duracion_segundos,
                   clock_timestamp() AS observado_en
            FROM public."Convocatoria" c
            JOIN public."Sesion" s ON s."idSesion" = c."idSesion"
            JOIN public."Caso" k ON k."idCaso" = s."idCaso"
            """;

    private final JdbcTemplate jdbc;

    public ProgramacionParticipacionJdbcAdapter(JdbcTemplate jdbc) {
        this.jdbc = Objects.requireNonNull(jdbc);
    }

    @Override
    public Optional<ContextoProgramacion> consultarSesion(UUID sesionId, UUID estudianteAutenticadoId) {
        return consultar(SELECT + " WHERE s.\"idSesion\" = ? AND c.\"idEstudiante\" = ?",
                sesionId, estudianteAutenticadoId);
    }

    @Override
    public Optional<ContextoProgramacion> consultarConvocatoria(UUID convocatoriaId, UUID estudianteAutenticadoId) {
        return consultar(SELECT + " WHERE c.\"idConvocatoria\" = ? AND c.\"idEstudiante\" = ?",
                convocatoriaId, estudianteAutenticadoId);
    }

    private Optional<ContextoProgramacion> consultar(String sql, UUID referencia, UUID estudiante) {
        Objects.requireNonNull(referencia, "La referencia es obligatoria");
        Objects.requireNonNull(estudiante, "El estudiante autenticado es obligatorio");
        try {
            List<ContextoProgramacion> rows = jdbc.query(sql, (rs, row) -> mapear(rs), referencia, estudiante);
            if (rows.size() > 1) {
                throw new ConsultaProgramacionNoDisponibleException();
            }
            return rows.stream().findFirst();
        } catch (DataAccessException | IllegalArgumentException ex) {
            throw new ConsultaProgramacionNoDisponibleException(ex);
        }
    }

    private ContextoProgramacion mapear(ResultSet rs) throws SQLException {
        return new ContextoProgramacion(
                rs.getObject("convocatoria_id", UUID.class), rs.getObject("estudiante_id", UUID.class),
                rs.getObject("sesion_id", UUID.class), rs.getObject("caso_id", UUID.class),
                rs.getString("estado_sesion"), rs.getString("estado_convocatoria"),
                Optional.ofNullable(instante(rs, "fecha_retiro")), rs.getString("estado_caso"),
                rs.getBoolean("caso_activo"), instante(rs, "inicio_previsto"), instante(rs, "fin_previsto"),
                Optional.ofNullable(instante(rs, "inicio_real")), Optional.ofNullable(instante(rs, "fin_real")),
                Duration.ofSeconds(rs.getInt("duracion_segundos")), instante(rs, "observado_en"));
    }

    private Instant instante(ResultSet rs, String columna) throws SQLException {
        OffsetDateTime value = rs.getObject(columna, OffsetDateTime.class);
        return value == null ? null : value.toInstant();
    }
}
