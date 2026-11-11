package com.prisaly.config;

import static org.junit.jupiter.api.Assertions.*;
import com.prisaly.PrisalyApplication;
import com.prisaly.m4.infrastructure.config.M4SchemaAutoConfiguration;
import com.prisaly.m4.infrastructure.config.M4SchemaContract;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.web.server.context.WebServerApplicationContext;
import org.springframework.jdbc.core.JdbcTemplate;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

@Testcontainers
class M4SchemaContratoPostgreSqlIT {
    @Container static PostgreSQLContainer db = new PostgreSQLContainer("postgres:16.15");
    JdbcTemplate jdbc;
    @BeforeEach void preparar() { jdbc = M4SchemaFixture.restaurar(db, false); }
    void rechaza(String contract) {
        assertTrue(assertThrows(IllegalStateException.class,
                () -> M4SchemaAutoConfiguration.verificar(jdbc, contract)).getMessage().startsWith("M4-ESQUEMA-PENDIENTE"));
    }
    void dd02() { jdbc = M4SchemaFixture.restaurar(db, true); }

    // P5 (DC-P5-FASE-B-01): the canonical location now ends in V013, so the default contract is bd0052-v013.
    @Test void historicoCompletoConservaIdentidadYDatos() {
        assertDoesNotThrow(() -> M4SchemaAutoConfiguration.verificar(jdbc));
        assertDoesNotThrow(() -> M4SchemaAutoConfiguration.verificar(jdbc, M4SchemaContract.V013));
        jdbc.update("INSERT INTO \"Rol\" (\"nombre\",\"descripcion\") VALUES ('QA','Fixture')");
        assertDoesNotThrow(() -> M4SchemaAutoConfiguration.verificar(jdbc));
        assertEquals(1, jdbc.queryForObject("SELECT count(*) FROM \"Rol\"", Integer.class));
    }
    @Test void mismaCantidadNoOcultaTablaHistoricaAusente() {
        jdbc.execute("ALTER TABLE \"Usuario\" RENAME TO \"UsuarioAjeno\"");
        // Regression: the former count-only guard accepted this corrupted schema (21 BD-005.2 tables + V013).
        assertEquals(22, jdbc.queryForObject("SELECT count(*) FROM information_schema.tables WHERE table_schema='public' AND table_type='BASE TABLE' AND table_name<>'flyway_schema_history'", Integer.class));
        rechaza(M4SchemaContract.VIGENTE);
    }
    @Test void tablaDesconocidaNoSeAdmite() {
        jdbc.execute("CREATE TABLE public.desconocida (id integer)"); rechaza(M4SchemaContract.VIGENTE);
    }
    // P5: @BeforeEach leaves the base at V013, and Flyway (without outOfOrder) refuses a lower pending version (E2),
    // so the archived V010 is applied from a clean base, in version order, as the test always intended.
    @Test void v010ParalelaSeRechazaSinBorrarHistorial() {
        var flyway = M4SchemaFixture.flyway(db, "classpath:db/migration", "classpath:db/m4-propuesta");
        flyway.clean(); flyway.migrate(); rechaza(M4SchemaContract.VIGENTE); rechaza(M4SchemaContract.HISTORICO);
        assertEquals(1, jdbc.queryForObject("SELECT count(*) FROM flyway_schema_history WHERE version='010' AND success", Integer.class));
    }
    @Test void dd02NoSeAdmitePorDefecto() { dd02(); rechaza(M4SchemaContract.VIGENTE); }
    @Test void dd02CompletoSeReconoceSoloConContratoExplicito() {
        dd02(); assertDoesNotThrow(() -> M4SchemaAutoConfiguration.verificar(jdbc, M4SchemaContract.V013_DD02));
        // The pre-P5 DD-02 contract does not accept the V013 table: no contract is relaxed.
        rechaza(M4SchemaContract.DD02);
    }
    @Test void historicoV009SinV013SigueReconociendoseSoloConSuContrato() throws Exception {
        jdbc = M4SchemaFixture.restaurarHistorico(db);
        assertDoesNotThrow(() -> M4SchemaAutoConfiguration.verificar(jdbc, M4SchemaContract.HISTORICO));
        rechaza(M4SchemaContract.V013);
        assertEquals(9, jdbc.queryForObject("SELECT count(*) FROM flyway_schema_history WHERE success", Integer.class));
    }
    // D-12 evidence: once V013 is applied, a later DD-02 V011/V012 is refused by Flyway (no outOfOrder); the history
    // is left untouched, so the proposals must take free numbers above 013 if ratified after V013.
    @Test void dd02PosteriorAV013SeRechazaSinOutOfOrderYNoAlteraHistorial() {
        var ex = assertThrows(org.flywaydb.core.api.exception.FlywayValidateException.class,
                () -> M4SchemaFixture.flyway(db, "classpath:db/migration", "classpath:db/dd02-propuesta").migrate());
        assertTrue(ex.getMessage().contains("011"), ex.getMessage());
        assertEquals("001,002,003,004,005,006,007,008,009,013", jdbc.queryForObject(
                "SELECT string_agg(version, ',' ORDER BY installed_rank) FROM flyway_schema_history", String.class));
        assertDoesNotThrow(() -> M4SchemaAutoConfiguration.verificar(jdbc));
    }
    @Test void ausenciaDeLaTablaV013SeDetecta() {
        jdbc.execute("DROP TABLE \"InicializacionAdministrador\"");
        rechaza(M4SchemaContract.V013);
    }
    @Test void contratoReconocidoConservaRechazoDeVinculoCruzadoEnAmbasTablas() {
        dd02();
        var usuarioA = java.util.UUID.randomUUID();
        var usuarioB = java.util.UUID.randomUUID();
        var estudianteB = java.util.UUID.randomUUID();
        jdbc.update("INSERT INTO \"Rol\" (\"idRol\",\"nombre\",\"descripcion\") VALUES (1,'QA','Fixture aislado')");
        jdbc.update("INSERT INTO \"Usuario\" (\"idUsuario\",\"idRol\",\"nombre\",\"correo\",\"estado\") VALUES (?,1,'A','a@qa.invalid','Activo'),(?,1,'B','b@qa.invalid','Activo')", usuarioA, usuarioB);
        jdbc.update("INSERT INTO \"Estudiante\" (\"idEstudiante\",\"idUsuario\") VALUES (?,?)", estudianteB, usuarioB);
        String activacion = "INSERT INTO \"ActivacionCuentaEstudiante\" (\"idActivacion\",\"idUsuario\",\"idEstudiante\",\"challengeId\",\"codigoHmac\",\"fechaExpiracion\") VALUES (?,?,?,gen_random_uuid(),?,now()+interval '15 minutes')";
        String sesion = "INSERT INTO \"SesionEstudiante\" (\"idSesionEstudiante\",\"idUsuario\",\"idEstudiante\",\"sesionTokenHmac\",\"fechaExpiracion\",\"fechaUltimaActividad\") VALUES (?,?,?,?,now()+interval '8 hours',now())";
        for (String sql : java.util.List.of(activacion, sesion)) {
            var error = assertThrows(org.springframework.dao.DataIntegrityViolationException.class,
                    () -> jdbc.update(sql, java.util.UUID.randomUUID(), usuarioA, estudianteB, new byte[32]));
            var causa = assertInstanceOf(java.sql.SQLException.class, error.getMostSpecificCause());
            assertEquals("23514", causa.getSQLState());
            assertTrue(causa.getMessage().contains("DD02-VINCULO-INCOHERENTE"));
        }
        assertEquals(0, jdbc.queryForObject("SELECT count(*) FROM \"ActivacionCuentaEstudiante\"", Integer.class));
        assertEquals(0, jdbc.queryForObject("SELECT count(*) FROM \"SesionEstudiante\"", Integer.class));
        // The same SQL accepts the coherent owner: the rejection above is not a blanket deny.
        assertEquals(1, jdbc.update(activacion, java.util.UUID.randomUUID(), usuarioB, estudianteB, new byte[32]));
        assertEquals(1, jdbc.update(sesion, java.util.UUID.randomUUID(), usuarioB, estudianteB, new byte[32]));
        assertDoesNotThrow(() -> M4SchemaAutoConfiguration.verificar(jdbc, M4SchemaContract.V013_DD02));
    }
    @Test void v011SinV012NoBasta() throws Exception {
        var directory = Files.createTempDirectory("m4-v011-only-");
        try (var sql = getClass().getResourceAsStream("/db/dd02-propuesta/V011__acceso_estudiante_cuenta.sql")) {
            assertNotNull(sql); Files.copy(sql, directory.resolve("V011__acceso_estudiante_cuenta.sql"));
            var flyway = M4SchemaFixture.flyway(db, "classpath:db/migration", "filesystem:" + directory.toAbsolutePath());
            flyway.clean(); flyway.migrate();
            rechaza(M4SchemaContract.V013_DD02);
        } finally {
            Files.deleteIfExists(directory.resolve("V011__acceso_estudiante_cuenta.sql")); Files.deleteIfExists(directory);
        }
    }
    @Test void fkEliminadaSeDetectaAunqueSiganLas21Tablas() {
        jdbc.execute("ALTER TABLE \"Convocatoria\" DROP CONSTRAINT fk_convocatoria_idestudiante"); rechaza(M4SchemaContract.VIGENTE);
    }
    @Test void triggerDeshabilitadoSeDetecta() {
        dd02(); jdbc.execute("ALTER TABLE \"SesionEstudiante\" DISABLE TRIGGER tr_sesion_estudiante_vinculo"); rechaza(M4SchemaContract.V013_DD02);
    }
    @Test void checkConMismoNombrePeroSinIntegridadSeDetecta() {
        dd02(); jdbc.execute("ALTER TABLE \"ActivacionCuentaEstudiante\" DROP CONSTRAINT ck_activacioncuentaestudiante_intentos, ADD CONSTRAINT ck_activacioncuentaestudiante_intentos CHECK (true)");
        rechaza(M4SchemaContract.V013_DD02);
    }
    @Test void funcionConMismoNombrePeroCuerpoDistintoSeDetecta() {
        dd02(); jdbc.execute("CREATE OR REPLACE FUNCTION public.prisaly_dd02_vinculo_coherente() RETURNS trigger LANGUAGE plpgsql AS $$ BEGIN RETURN NEW; END $$");
        rechaza(M4SchemaContract.V013_DD02);
    }
    @Test void historialAlteradoNoSeConfundeConEsquemaValido() {
        jdbc.update("UPDATE flyway_schema_history SET checksum=0 WHERE version='009'"); rechaza(M4SchemaContract.VIGENTE);
        assertEquals(0, jdbc.queryForObject("SELECT checksum FROM flyway_schema_history WHERE version='009'", Integer.class));
    }
    @Test void springArrancaSobreHistorico() throws Exception { arrancar(false); }
    @Test void springArrancaSobreCandidataSinHabilitarIdentidadEstudiantil() throws Exception { dd02(); arrancar(true); }

    void arrancar(boolean dd02) throws Exception {
        try (var context = new SpringApplicationBuilder(PrisalyApplication.class).run(
                "--server.port=0", "--server.address=127.0.0.1",
                "--spring.datasource.url=" + db.getJdbcUrl(), "--spring.datasource.username=" + db.getUsername(),
                "--spring.datasource.password=" + db.getPassword(),
                "--spring.flyway.locations=" + (dd02 ? "classpath:db/migration,classpath:db/dd02-propuesta" : "classpath:db/migration"),
                "--prisaly.m4.schema-contract=" + (dd02 ? M4SchemaContract.V013_DD02 : M4SchemaContract.V013),
                "--prisaly.m1.student-access.enabled=false", "--prisaly.m7.worker.enabled=false")) {
            int port = ((WebServerApplicationContext) context).getWebServer().getPort();
            var client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
            String base = "http://127.0.0.1:" + port + "/api";
            var health = client.send(HttpRequest.newBuilder(URI.create(base + "/health")).GET().build(), HttpResponse.BodyHandlers.ofString());
            assertEquals(200, health.statusCode()); assertTrue(health.body().contains("prisaly-backend"));
            assertEquals(401, client.send(HttpRequest.newBuilder(URI.create(base + "/v1/personal/me")).GET().build(), HttpResponse.BodyHandlers.ofString()).statusCode());
            var disabled = client.send(HttpRequest.newBuilder(URI.create(base + "/v1/acceso-estudiante/validar"))
                    .header("Content-Type", "application/json").POST(HttpRequest.BodyPublishers.ofString("{\"challengeId\":\"fixture\",\"codigo\":\"00000000\"}")).build(), HttpResponse.BodyHandlers.ofString());
            assertEquals(501, disabled.statusCode()); assertTrue(disabled.body().contains("ESTUDIANTE_ACCESS_PENDING_CONTRACT"));
        }
    }
}
