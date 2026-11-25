package com.prisaly.m1.infrastructure.persistence;

import com.prisaly.m1.application.port.EstudianteRegistroPort;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/** P-78 (DC-79): JDBC access to the official {@code "Estudiante"} table. Writes join the caller's transaction. */
public class EstudianteRegistroJdbcAdapter implements EstudianteRegistroPort {
    private final JdbcTemplate jdbc;

    public EstudianteRegistroJdbcAdapter(JdbcTemplate jdbc) {
        this.jdbc = Objects.requireNonNull(jdbc, "jdbc");
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public void registrar(UUID idEstudiante, UUID idUsuario) {
        jdbc.update("INSERT INTO \"Estudiante\" (\"idEstudiante\", \"idUsuario\") VALUES (?, ?)", idEstudiante, idUsuario);
    }

    @Override
    @Transactional(readOnly = true)
    public List<EstudianteRegistrado> buscarPorCorreo(String correo) {
        String normalizado = correo == null ? "" : correo.trim().toLowerCase(Locale.ROOT);
        return jdbc.query("SELECT u.\"idUsuario\", e.\"idEstudiante\", u.\"nombre\", u.\"correo\", u.\"estado\""
                + " FROM \"Estudiante\" e JOIN \"Usuario\" u ON u.\"idUsuario\" = e.\"idUsuario\""
                + " WHERE lower(u.\"correo\") = ?",
                (rs, n) -> new EstudianteRegistrado(rs.getObject(1, UUID.class), rs.getObject(2, UUID.class),
                        rs.getString(3), rs.getString(4), rs.getString(5)), normalizado);
    }
}
