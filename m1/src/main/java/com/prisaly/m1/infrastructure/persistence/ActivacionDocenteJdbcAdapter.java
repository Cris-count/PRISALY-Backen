package com.prisaly.m1.infrastructure.persistence;

import com.prisaly.m1.application.port.ActivacionDocenteRepositorioPort;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * D-05 (DC-BE-D05-AUTORIZACION-QA-77): JDBC adapter over the candidate table {@code "ActivacionDocente"}
 * ({@code db/d05-propuesta}). Every operation joins the caller's write transaction (MANDATORY): the token row lock
 * taken by {@link #bloquearPorHash} lasts until the consumption and the password update commit together.
 * Only SHA-256 hashes reach this class.
 */
public class ActivacionDocenteJdbcAdapter implements ActivacionDocenteRepositorioPort {
    private static final String COLUMNAS =
            "\"hashToken\", \"idUsuario\", \"idEmisor\", \"emitidoEn\", \"expiraEn\", \"consumidoEn\", \"revocadoEn\"";
    private final JdbcTemplate jdbc;

    public ActivacionDocenteJdbcAdapter(JdbcTemplate jdbc) {
        this.jdbc = Objects.requireNonNull(jdbc, "jdbc");
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public void registrar(RegistroActivacion r) {
        jdbc.update("INSERT INTO \"ActivacionDocente\" (" + COLUMNAS + ") VALUES (?, ?, ?, ?, ?, ?, ?)",
                r.hashToken(), r.idUsuario(), r.emisor(), ts(r.emitidoEn()), ts(r.expiraEn()),
                ts(r.consumidoEn()), ts(r.revocadoEn()));
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public int revocarVigentes(UUID idUsuario, Instant instante) {
        return jdbc.update("UPDATE \"ActivacionDocente\" SET \"revocadoEn\" = ? WHERE \"idUsuario\" = ?"
                + " AND \"consumidoEn\" IS NULL AND \"revocadoEn\" IS NULL", ts(instante), idUsuario);
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public Optional<RegistroActivacion> bloquearPorHash(String hashToken) {
        List<RegistroActivacion> filas = jdbc.query("SELECT " + COLUMNAS
                + " FROM \"ActivacionDocente\" WHERE \"hashToken\" = ? FOR UPDATE", ActivacionDocenteJdbcAdapter::fila,
                hashToken);
        return filas.stream().findFirst();
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public boolean marcarConsumido(String hashToken, Instant instante) {
        return jdbc.update("UPDATE \"ActivacionDocente\" SET \"consumidoEn\" = ? WHERE \"hashToken\" = ?"
                + " AND \"consumidoEn\" IS NULL AND \"revocadoEn\" IS NULL", ts(instante), hashToken) == 1;
    }

    private static RegistroActivacion fila(ResultSet rs, int n) throws SQLException {
        return new RegistroActivacion(rs.getString(1).trim(), rs.getObject(2, UUID.class), rs.getObject(3, UUID.class),
                instante(rs.getTimestamp(4)), instante(rs.getTimestamp(5)), instante(rs.getTimestamp(6)),
                instante(rs.getTimestamp(7)));
    }

    private static Timestamp ts(Instant i) {
        return i == null ? null : Timestamp.from(i);
    }

    private static Instant instante(Timestamp t) {
        return t == null ? null : t.toInstant();
    }
}
