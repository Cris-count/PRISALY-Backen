package com.prisaly.config;

import static org.junit.jupiter.api.Assertions.*;

import com.prisaly.m4.infrastructure.config.M4SchemaAutoConfiguration;
import com.prisaly.m4.infrastructure.config.M4SchemaContract;
import java.util.UUID;
import org.flywaydb.core.api.exception.FlywayValidateException;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

/**
 * DC-BE-CIERRE-DEFINITIVO-87 — technical evidence for D-ESQ-1 on disposable PostgreSQL 16.15 (never a shared base).
 * The integral candidate orders the candidates V014-V016 (DD-02 B), V019 (N2) and V020 (D-05) after V013, without the
 * simulator V017/V018. It proves: (1) clean install, (2) upgrade of an official V013 base keeping its data, (3) the
 * exact conflict of a base that already applied V020 alone (the QA FE-60 situation): Flyway refuses the lower
 * versions without outOfOrder. FINDING (DC-87, observed on the first run): forcing a one-off outOfOrder run yields the
 * same structure and the same SET of migrations, and the M4 guard ACCEPTS it, because its history fingerprint ignores
 * the installed order. That is a technical path for QA FE-60 that D-ESQ-1 must accept or reject explicitly.
 */
@Testcontainers
class MigracionesIntegralPostgreSqlIT {
    @Container static PostgreSQLContainer db = new PostgreSQLContainer("postgres:16.15");

    static final String[] INTEGRAL = {"classpath:db/migration", "classpath:db/dd02b-propuesta",
        "classpath:db/m6-propuesta", "classpath:db/d05-propuesta"};

    private String historial(JdbcTemplate jdbc) {
        return jdbc.queryForObject("SELECT string_agg(version, ',' ORDER BY installed_rank) FROM flyway_schema_history"
                + " WHERE success", String.class);
    }

    @Test
    void instalacionLimpiaAplicaLaCadenaIntegralEnOrdenYCumpleSuContrato() {
        var flyway = M4SchemaFixture.flyway(db, INTEGRAL);
        flyway.clean();
        assertEquals(15, flyway.migrate().migrationsExecuted);
        flyway.validate();
        var jdbc = M4SchemaFixture.jdbc(db);
        assertEquals("001,002,003,004,005,006,007,008,009,013,014,015,016,019,020", historial(jdbc));
        assertDoesNotThrow(() -> M4SchemaAutoConfiguration.verificar(jdbc, M4SchemaContract.V013_INTEGRAL));
        // The integral schema is NOT the shipped one: the default contract and the partial ones reject it.
        for (String otro : new String[] {M4SchemaContract.VIGENTE, M4SchemaContract.V013_DD02B, M4SchemaContract.V013_D05}) {
            assertThrows(IllegalStateException.class, () -> M4SchemaAutoConfiguration.verificar(jdbc, otro), otro);
        }
    }

    @Test
    void actualizacionDesdeV013OficialConservaLosDatosYCumpleElContrato() {
        var oficial = M4SchemaFixture.flyway(db, "classpath:db/migration");
        oficial.clean();
        oficial.migrate();
        var jdbc = M4SchemaFixture.jdbc(db);
        assertDoesNotThrow(() -> M4SchemaAutoConfiguration.verificar(jdbc, M4SchemaContract.VIGENTE));
        UUID usuario = UUID.randomUUID(), estudiante = UUID.randomUUID();
        jdbc.update("INSERT INTO \"Rol\" (\"idRol\",\"nombre\",\"descripcion\") VALUES (900,'Actualizacion87','fixture')");
        jdbc.update("INSERT INTO \"Usuario\" (\"idUsuario\",\"idRol\",\"nombre\",\"correo\") VALUES (?,900,'Previo','previo87@prisaly.test')", usuario);
        jdbc.update("INSERT INTO \"Estudiante\" (\"idEstudiante\",\"idUsuario\") VALUES (?,?)", estudiante, usuario);

        var integral = M4SchemaFixture.flyway(db, INTEGRAL);
        assertEquals(5, integral.migrate().migrationsExecuted, "only V014-V016, V019, V020 are pending");
        integral.validate();
        assertEquals("001,002,003,004,005,006,007,008,009,013,014,015,016,019,020", historial(jdbc));
        assertDoesNotThrow(() -> M4SchemaAutoConfiguration.verificar(jdbc, M4SchemaContract.V013_INTEGRAL));
        assertEquals(usuario, jdbc.queryForObject("SELECT \"idUsuario\" FROM \"Estudiante\" WHERE \"idEstudiante\"=?",
                UUID.class, estudiante), "pre-existing data survives the upgrade");
    }

    @Test
    void baseConV020AisladaComoQaFe60NoPuedeIncorporarLaCadenaSinDecisionDesq1() {
        var d05 = M4SchemaFixture.flyway(db, "classpath:db/migration", "classpath:db/d05-propuesta");
        d05.clean();
        d05.migrate();
        var jdbc = M4SchemaFixture.jdbc(db);
        assertDoesNotThrow(() -> M4SchemaAutoConfiguration.verificar(jdbc, M4SchemaContract.V013_D05));

        var ex = assertThrows(FlywayValidateException.class, () -> M4SchemaFixture.flyway(db, INTEGRAL).migrate());
        assertTrue(ex.getMessage().contains("014"), ex.getMessage());
        assertEquals("001,002,003,004,005,006,007,008,009,013,020", historial(jdbc), "history untouched");

        // A one-off outOfOrder run applies 014-016 and 019 after 020: different installed order, same set and structure.
        org.flywaydb.core.Flyway.configure().dataSource(db.getJdbcUrl(), db.getUsername(), db.getPassword())
                .locations(INTEGRAL).outOfOrder(true).load().migrate();
        assertEquals("001,002,003,004,005,006,007,008,009,013,020,014,015,016,019", historial(jdbc));
        assertDoesNotThrow(() -> M4SchemaAutoConfiguration.verificar(jdbc, M4SchemaContract.V013_INTEGRAL),
                "the M4 fingerprint is order-insensitive: the guard accepts the out-of-order base (D-ESQ-1 finding)");
        // Afterwards the normal (in-order) Flyway validation accepts the base as well: nothing is left pending.
        assertDoesNotThrow(() -> M4SchemaFixture.flyway(db, INTEGRAL).validate());
    }
}
