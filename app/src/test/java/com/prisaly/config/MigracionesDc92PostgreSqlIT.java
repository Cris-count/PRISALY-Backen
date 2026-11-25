package com.prisaly.config;

import static org.junit.jupiter.api.Assertions.*;

import com.prisaly.m4.infrastructure.config.M4SchemaAutoConfiguration;
import com.prisaly.m4.infrastructure.config.M4SchemaContract;
import java.util.List;
import java.util.UUID;
import org.flywaydb.core.api.exception.FlywayValidateException;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

/**
 * DC-96 — the complete candidate chain V014-V023 (DD-02 B V014-V016, N2 V019, D-05 V020, DC-92 V021-V023) on a
 * disposable PostgreSQL 16.15 only, never a shared base. Complements {@link MigracionesIntegralPostgreSqlIT} (which
 * stops at V020) with: clean install of the whole chain, upgrade from the integral base keeping data, exclusivity of the
 * {@code bd0052-v013-integral-dc92} contract, the DC-92 integrity objects (insert-only triggers, one-repetition CHECK),
 * and the two ordering hazards D-ESQ-1 must settle: a QA FE-60-like base (V020 alone) and a simulator (V017/V018,
 * D-SIM) promoted after the chain.
 */
@Testcontainers
class MigracionesDc92PostgreSqlIT {
    @Container static PostgreSQLContainer db = new PostgreSQLContainer("postgres:16.15");

    static final String[] INTEGRAL = MigracionesIntegralPostgreSqlIT.INTEGRAL;
    static final String[] DC92 = {"classpath:db/migration", "classpath:db/dd02b-propuesta",
        "classpath:db/m6-propuesta", "classpath:db/d05-propuesta", "classpath:db/dc92-propuesta"};
    static final String CADENA = "001,002,003,004,005,006,007,008,009,013,014,015,016,019,020,021,022,023";

    private String historial(JdbcTemplate jdbc) {
        return jdbc.queryForObject("SELECT string_agg(version, ',' ORDER BY installed_rank) FROM flyway_schema_history"
                + " WHERE success", String.class);
    }

    @Test
    void instalacionLimpiaAplicaV014aV023EnOrdenYSoloCumpleElContratoDc92() {
        var flyway = M4SchemaFixture.flyway(db, DC92);
        flyway.clean();
        assertEquals(18, flyway.migrate().migrationsExecuted);
        flyway.validate();
        var jdbc = M4SchemaFixture.jdbc(db);
        assertEquals(CADENA, historial(jdbc));
        assertDoesNotThrow(() -> M4SchemaAutoConfiguration.verificar(jdbc, M4SchemaContract.V013_INTEGRAL_DC92));
        for (String otro : new String[] {M4SchemaContract.VIGENTE, M4SchemaContract.V013_INTEGRAL,
            M4SchemaContract.V013_DD02B, M4SchemaContract.V013_D05}) {
            assertThrows(IllegalStateException.class, () -> M4SchemaAutoConfiguration.verificar(jdbc, otro), otro);
        }
    }

    @Test
    void objetosDeIntegridadDc92ExistenTrasLaMigracion() {
        var flyway = M4SchemaFixture.flyway(db, DC92);
        flyway.clean();
        flyway.migrate();
        var jdbc = M4SchemaFixture.jdbc(db);
        List<String> triggers = jdbc.queryForList("SELECT tgname FROM pg_trigger WHERE NOT tgisinternal AND tgrelid IN"
                + " ('public.\"SeleccionIntento\"'::regclass, 'public.\"ClasificacionAusencia\"'::regclass,"
                + " 'public.\"RetiroConvocatoria\"'::regclass) ORDER BY tgname", String.class);
        assertEquals(List.of("tr_clasificacion_ausencia_inmutable", "tr_retiro_convocatoria_inmutable",
                "tr_seleccion_intento_inmutable", "tr_seleccion_intento_valido"), triggers);
        assertEquals("CHECK (((\"numeroIntento\" >= 1) AND (\"numeroIntento\" <= 2)))", jdbc.queryForObject(
                "SELECT pg_get_constraintdef(oid) FROM pg_constraint WHERE conname='ck_intento_una_repeticion_dd04'", String.class));
        // Insert-only tables: the immutability function rejects UPDATE/DELETE even on an empty statement target.
        assertEquals(3, jdbc.queryForObject("SELECT count(*) FROM pg_trigger t JOIN pg_proc p ON p.oid=t.tgfoid"
                + " WHERE p.proname='prisaly_historial_dc92_inmutable' AND NOT t.tgisinternal", Integer.class));
    }

    @Test
    void actualizacionDesdeLaBaseIntegralAplicaSoloV021aV023YConservaLosDatos() {
        var integral = M4SchemaFixture.flyway(db, INTEGRAL);
        integral.clean();
        integral.migrate();
        var jdbc = M4SchemaFixture.jdbc(db);
        assertDoesNotThrow(() -> M4SchemaAutoConfiguration.verificar(jdbc, M4SchemaContract.V013_INTEGRAL));
        UUID usuario = UUID.randomUUID(), estudiante = UUID.randomUUID();
        jdbc.update("INSERT INTO \"Rol\" (\"idRol\",\"nombre\",\"descripcion\") VALUES (901,'Actualizacion96','fixture')");
        jdbc.update("INSERT INTO \"Usuario\" (\"idUsuario\",\"idRol\",\"nombre\",\"correo\") VALUES (?,901,'Previo','previo96@prisaly.test')", usuario);
        jdbc.update("INSERT INTO \"Estudiante\" (\"idEstudiante\",\"idUsuario\") VALUES (?,?)", estudiante, usuario);

        var dc92 = M4SchemaFixture.flyway(db, DC92);
        assertEquals(3, dc92.migrate().migrationsExecuted, "only V021-V023 are pending");
        dc92.validate();
        assertEquals(CADENA, historial(jdbc));
        assertDoesNotThrow(() -> M4SchemaAutoConfiguration.verificar(jdbc, M4SchemaContract.V013_INTEGRAL_DC92));
        assertEquals(usuario, jdbc.queryForObject("SELECT \"idUsuario\" FROM \"Estudiante\" WHERE \"idEstudiante\"=?",
                UUID.class, estudiante), "pre-existing data survives the upgrade");
    }

    @Test
    void baseConV020AisladaComoQaFe60TampocoAceptaLaCadenaDc92SinDecisionDesq1() {
        var d05 = M4SchemaFixture.flyway(db, "classpath:db/migration", "classpath:db/d05-propuesta");
        d05.clean();
        d05.migrate();
        var jdbc = M4SchemaFixture.jdbc(db);
        var ex = assertThrows(FlywayValidateException.class, () -> M4SchemaFixture.flyway(db, DC92).migrate());
        assertTrue(ex.getMessage().contains("014"), ex.getMessage());
        assertEquals("001,002,003,004,005,006,007,008,009,013,020", historial(jdbc), "history untouched");
    }

    @Test
    void simuladorV017V018PromovidoDespuesDeLaCadenaEsRechazadoSinOutOfOrder() {
        var dc92 = M4SchemaFixture.flyway(db, DC92);
        dc92.clean();
        dc92.migrate();
        var jdbc = M4SchemaFixture.jdbc(db);
        String[] conSimulador = {"classpath:db/migration", "classpath:db/dd02b-propuesta", "classpath:db/m6-propuesta",
            "classpath:db/d05-propuesta", "classpath:db/dc92-propuesta", "classpath:db/sim-propuesta",
            "classpath:db/sim-propuesta-b"};
        var ex = assertThrows(FlywayValidateException.class, () -> M4SchemaFixture.flyway(db, conSimulador).migrate());
        assertTrue(ex.getMessage().contains("017"), ex.getMessage());
        assertEquals(CADENA, historial(jdbc), "D-SIM: V017/V018 must be renumbered above V023 or decided before promotion");
    }
}
