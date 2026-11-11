package com.prisaly.m1.infrastructure.persistence;

import com.prisaly.m1.application.port.InicializacionAdministradorPort;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/** P5 / DM1-01: V013 marker. MANDATORY propagation: it only works inside the initialization transaction. */
@Repository
public class InicializacionAdministradorJdbcAdapter implements InicializacionAdministradorPort {

    /** Fixed advisory-lock key reserved for the first-administrator initialization. */
    static final long CLAVE_BLOQUEO = 0x5052_4953_0001L;

    private final JdbcTemplate jdbc;

    public InicializacionAdministradorJdbcAdapter(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public void bloquear() {
        jdbc.query("SELECT pg_advisory_xact_lock(?)", rs -> { }, CLAVE_BLOQUEO);
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public boolean marcadorExiste() {
        Boolean existe = jdbc.queryForObject("SELECT EXISTS (SELECT 1 FROM \"InicializacionAdministrador\")", Boolean.class);
        return Boolean.TRUE.equals(existe);
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public void registrarMarcador(UUID idUsuarioAdministrador, String origen) {
        jdbc.update("INSERT INTO \"InicializacionAdministrador\" (\"idUsuarioAdministrador\", \"origen\") VALUES (?, ?)",
                idUsuarioAdministrador, origen);
    }
}
