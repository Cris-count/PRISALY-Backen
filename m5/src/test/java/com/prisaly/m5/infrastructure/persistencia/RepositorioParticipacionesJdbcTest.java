package com.prisaly.m5.infrastructure.persistencia;

import com.prisaly.m5.application.PersistenciaIntentoException;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.sql.SQLException;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;

import com.prisaly.m5.application.ReferenciaConvocatoria;

/**
 * Lógica del adaptador sin base. Las sentencias, bloqueos y triggers reales se
 * prueban en {@code InicioIntentoIT} (PostgreSQL 16.15, V001–V009).
 */
@DisplayName("RepositorioParticipacionesJdbc (sin base)")
class RepositorioParticipacionesJdbcTest {

    private static DataIntegrityViolationException envuelta(SQLException causa) {
        return new DataIntegrityViolationException("insert", causa);
    }

    @Test
    @DisplayName("reconoce el rechazo de repetición de V005 por SQLSTATE y mensaje")
    void rechazoDeRepeticion() {
        assertTrue(RepositorioParticipacionesJdbc.esRechazoDeRepeticion(envuelta(
                new SQLException("ERROR: Repetición exige autorización expresa", "23514"))));
    }

    @Test
    @DisplayName("otras violaciones no se confunden con una repetición")
    void otrasViolaciones() {
        assertFalse(RepositorioParticipacionesJdbc.esRechazoDeRepeticion(envuelta(
                new SQLException("violates check constraint \"ck_intento_tiempos\"", "23514"))));
        assertFalse(RepositorioParticipacionesJdbc.esRechazoDeRepeticion(envuelta(
                new SQLException("Repetición exige autorización expresa", "23505"))));
        assertFalse(RepositorioParticipacionesJdbc.esRechazoDeRepeticion(envuelta(new SQLException((String) null, "23514"))));
        assertFalse(RepositorioParticipacionesJdbc.esRechazoDeRepeticion(new DataIntegrityViolationException("sin causa")));
    }

    @Test
    @DisplayName("una referencia que no es uuid no llega a la base")
    void referenciaNoUuid() {
        JdbcTemplate sinBase = new JdbcTemplate() {
            @Override
            public <T> java.util.List<T> queryForList(String sql, Class<T> tipo, Object... argumentos) {
                throw new AssertionError("no debe consultar");
            }
        };
        assertThrows(PersistenciaIntentoException.class, () -> new RepositorioParticipacionesJdbc(sinBase)
                .bloquear(new ReferenciaConvocatoria("no-es-uuid")));
    }

    @Test
    @DisplayName("requiere la base")
    void requiereBase() {
        assertThrows(IllegalArgumentException.class, () -> new RepositorioParticipacionesJdbc(null));
    }
}
