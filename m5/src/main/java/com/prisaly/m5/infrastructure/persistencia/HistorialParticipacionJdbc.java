package com.prisaly.m5.infrastructure.persistencia;

import java.sql.PreparedStatement;
import java.util.Collection;
import java.util.Objects;
import java.util.UUID;

import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.prisaly.m5.api.HistorialParticipacionPort;

/**
 * Implementación de {@link HistorialParticipacionPort} sobre la tabla
 * {@code "Participacion"} de M5. Solo lee; exige la transacción del llamador
 * ({@link Propagation#MANDATORY}).
 */
@Component
public class HistorialParticipacionJdbc implements HistorialParticipacionPort {

    static final String EXISTE_PARTICIPACION =
            "SELECT EXISTS (SELECT 1 FROM public.\"Participacion\" WHERE \"idConvocatoria\" = ANY (?))";

    private final JdbcTemplate jdbc;

    public HistorialParticipacionJdbc(JdbcTemplate jdbc) {
        this.jdbc = Objects.requireNonNull(jdbc, "jdbc");
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public boolean existeParticipacion(Collection<UUID> convocatorias) {
        if (convocatorias == null || convocatorias.stream().anyMatch(Objects::isNull)) {
            throw new IllegalArgumentException("Las convocatorias son obligatorias y no admiten nulos");
        }
        if (convocatorias.isEmpty()) {
            return false;
        }
        Object[] ids = convocatorias.toArray();
        try {
            Boolean existe = jdbc.query(conexion -> {
                PreparedStatement sentencia = conexion.prepareStatement(EXISTE_PARTICIPACION);
                sentencia.setArray(1, conexion.createArrayOf("uuid", ids));
                return sentencia;
            }, fila -> fila.next() && fila.getBoolean(1));
            return Boolean.TRUE.equals(existe);
        } catch (DataAccessException e) {
            throw new PersistenciaIntentoException("No se pudo consultar el historial de participación", e);
        }
    }
}
