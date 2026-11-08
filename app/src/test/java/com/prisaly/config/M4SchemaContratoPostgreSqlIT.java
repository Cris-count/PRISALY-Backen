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

    @Test void historicoCompletoConservaIdentidadYDatos() {
        assertDoesNotThrow(() -> M4SchemaAutoConfiguration.verificar(jdbc));
        jdbc.update("INSERT INTO \"Rol\" (\"nombre\",\"descripcion\") VALUES ('QA','Fixture')");
        assertDoesNotThrow(() -> M4SchemaAutoConfiguration.verificar(jdbc));
        assertEquals(1, jdbc.queryForObject("SELECT count(*) FROM \"Rol\"", Integer.class));
    }
    @Test void mismaCantidadNoOcultaTablaHistoricaAusente() {
        jdbc.execute("ALTER TABLE \"Usuario\" RENAME TO \"UsuarioAjeno\"");
        // Regression: the former count-only guard accepted this corrupted 21-table schema.
        assertEquals(21, jdbc.queryForObject("SELECT count(*) FROM information_schema.tables WHERE table_schema='public' AND table_type='BASE TABLE' AND table_name<>'flyway_schema_history'", Integer.class));
        rechaza(M4SchemaContract.HISTORICO);
    }
    @Test void tablaDesconocidaNoSeAdmite() {
        jdbc.execute("CREATE TABLE public.desconocida (id integer)"); rechaza(M4SchemaContract.HISTORICO);
    }
    @Test void v010ParalelaSeRechazaSinBorrarHistorial() {
        var flyway = M4SchemaFixture.flyway(db, "classpath:db/migration", "classpath:db/m4-propuesta");
        flyway.migrate(); rechaza(M4SchemaContract.HISTORICO);
        assertEquals(1, jdbc.queryForObject("SELECT count(*) FROM flyway_schema_history WHERE version='010' AND success", Integer.class));
    }
    @Test void dd02NoSeAdmitePorDefecto() { dd02(); rechaza(M4SchemaContract.HISTORICO); }
    @Test void dd02CompletoSeReconoceSoloConContratoExplicito() {
        dd02(); assertDoesNotThrow(() -> M4SchemaAutoConfiguration.verificar(jdbc, M4SchemaContract.DD02));
    }
    @Test void v011SinV012NoBasta() throws Exception {
        var directory = Files.createTempDirectory("m4-v011-only-");
        try (var sql = getClass().getResourceAsStream("/db/dd02-propuesta/V011__acceso_estudiante_cuenta.sql")) {
            assertNotNull(sql); Files.copy(sql, directory.resolve("V011__acceso_estudiante_cuenta.sql"));
            M4SchemaFixture.flyway(db, "classpath:db/migration", "filesystem:" + directory.toAbsolutePath()).migrate();
            rechaza(M4SchemaContract.DD02);
        } finally {
            Files.deleteIfExists(directory.resolve("V011__acceso_estudiante_cuenta.sql")); Files.deleteIfExists(directory);
        }
    }
    @Test void fkEliminadaSeDetectaAunqueSiganLas21Tablas() {
        jdbc.execute("ALTER TABLE \"Convocatoria\" DROP CONSTRAINT fk_convocatoria_idestudiante"); rechaza(M4SchemaContract.HISTORICO);
    }
    @Test void triggerDeshabilitadoSeDetecta() {
        dd02(); jdbc.execute("ALTER TABLE \"SesionEstudiante\" DISABLE TRIGGER tr_sesion_estudiante_vinculo"); rechaza(M4SchemaContract.DD02);
    }
    @Test void checkConMismoNombrePeroSinIntegridadSeDetecta() {
        dd02(); jdbc.execute("ALTER TABLE \"ActivacionCuentaEstudiante\" DROP CONSTRAINT ck_activacioncuentaestudiante_intentos, ADD CONSTRAINT ck_activacioncuentaestudiante_intentos CHECK (true)");
        rechaza(M4SchemaContract.DD02);
    }
    @Test void funcionConMismoNombrePeroCuerpoDistintoSeDetecta() {
        dd02(); jdbc.execute("CREATE OR REPLACE FUNCTION public.prisaly_dd02_vinculo_coherente() RETURNS trigger LANGUAGE plpgsql AS $$ BEGIN RETURN NEW; END $$");
        rechaza(M4SchemaContract.DD02);
    }
    @Test void historialAlteradoNoSeConfundeConEsquemaValido() {
        jdbc.update("UPDATE flyway_schema_history SET checksum=0 WHERE version='009'"); rechaza(M4SchemaContract.HISTORICO);
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
                "--prisaly.m4.schema-contract=" + (dd02 ? M4SchemaContract.DD02 : M4SchemaContract.HISTORICO),
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
