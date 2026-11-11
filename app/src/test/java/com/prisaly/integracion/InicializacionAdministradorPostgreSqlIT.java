package com.prisaly.integracion;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.prisaly.PrisalyApplication;
import com.prisaly.m1.api.IdentityDomainException;
import com.prisaly.m1.application.port.InicializacionAdministradorPort;
import com.prisaly.m1.application.port.RolRepositorioPort;
import com.prisaly.m1.application.service.IdentityCatalogService;
import com.prisaly.m1.application.service.InicializacionAdministradorService;
import com.prisaly.m1.application.service.InicializacionAdministradorService.Estado;
import com.prisaly.m1.domain.rule.PoliticaContrasena;
import com.prisaly.m1.infrastructure.security.InicializacionAdministradorRunner;
import com.prisaly.m4.infrastructure.config.M4SchemaAutoConfiguration;
import com.prisaly.m4.infrastructure.config.M4SchemaContract;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.postgresql.PostgreSQLContainer;
import tools.jackson.databind.ObjectMapper;

/**
 * P5 / DM1-01 (DC-P5-FASE-B-01) over the real APP and PostgreSQL 16.15: P5-I1 to P5-I5, P5-C1, the runner end to end
 * and D-04 (DOCENTE from PD-01). Every test starts from a clean V001-V009 + V013 schema plus PD-01: the marker is
 * immutable by design, so the schema is rebuilt instead of deleting rows.
 */
@SpringBootTest(classes = PrisalyApplication.class, webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ExtendWith(OutputCaptureExtension.class)
class InicializacionAdministradorPostgreSqlIT {
    static final String CLAVE = "Fixture#PrimerAdmin-2026";
    static final PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:16.15");
    static { postgres.start(); }

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry r) {
        r.add("spring.datasource.url", postgres::getJdbcUrl);
        r.add("spring.datasource.username", postgres::getUsername);
        r.add("spring.datasource.password", postgres::getPassword);
        r.add("spring.jpa.hibernate.ddl-auto", () -> "validate");
        r.add("spring.jpa.properties.hibernate.globally_quoted_identifiers", () -> "true");
        r.add("spring.flyway.enabled", () -> "true");
        r.add("prisaly.cors.allowed-origins", () -> "http://localhost:4200");
        r.add("prisaly.m1.student-access.enabled", () -> "false");
        r.add("prisaly.m7.worker.enabled", () -> "false");
        // No override of prisaly.m1.administracion.rol-docente: the shipped default (DOCENTE) is under test.
    }

    @LocalServerPort int port;
    @Autowired InicializacionAdministradorService servicio;
    @Autowired InicializacionAdministradorPort marcador;
    @Autowired RolRepositorioPort roles;
    @Autowired IdentityCatalogService catalogo;
    @Autowired PlatformTransactionManager tx;
    @Autowired JdbcTemplate jdbc;
    @Autowired ObjectMapper json;
    @TempDir Path dir;
    final HttpClient http = HttpClient.newHttpClient();

    @BeforeEach
    void esquemaLimpioConPd01() throws Exception {
        Flyway flyway = Flyway.configure()
                .dataSource(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword())
                .locations("classpath:db/migration").cleanDisabled(false).load();
        flyway.clean();
        flyway.migrate();
        pd01();
    }

    private void pd01() throws Exception {
        jdbc.execute(Files.readString(Path.of("../docs/provision/pd01-inicializacion.sql"), StandardCharsets.UTF_8));
    }

    private int cantidad(String sql, Object... args) {
        return jdbc.queryForObject(sql, Integer.class, args);
    }

    private int administradores() {
        return cantidad("SELECT count(*) FROM \"Usuario\" u JOIN \"Rol\" r ON r.\"idRol\"=u.\"idRol\" WHERE r.\"nombre\"='ADMINISTRADOR'");
    }

    private int marcadores() {
        return cantidad("SELECT count(*) FROM \"InicializacionAdministrador\"");
    }

    private HttpResponse<String> send(String method, String path, String token, String body) throws Exception {
        var b = HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/api" + path)).method(method,
                body == null ? HttpRequest.BodyPublishers.noBody() : HttpRequest.BodyPublishers.ofString(body));
        if (body != null) b.header("Content-Type", "application/json");
        if (token != null) b.header("Authorization", "Bearer " + token);
        return http.send(b.build(), HttpResponse.BodyHandlers.ofString());
    }

    private String login(String correo, String clave) throws Exception {
        var r = send("POST", "/v1/personal/autenticar", null, "{\"correo\":\"" + correo + "\",\"password\":\"" + clave + "\"}");
        assertEquals(200, r.statusCode(), r.body());
        return json.readTree(r.body()).get("accessToken").asText();
    }

    private static String sqlState(Throwable error) {
        for (Throwable t = error; t != null; t = t.getCause()) {
            if (t instanceof SQLException s) return s.getSQLState();
        }
        return null;
    }

    // P5-C1: the application started on V001-V009 + V013 with the default guard contract.
    @Test void esquemaVigenteEsV013YLaGuardaM4LoAcepta() {
        assertDoesNotThrow(() -> M4SchemaAutoConfiguration.verificar(jdbc));
        assertDoesNotThrow(() -> M4SchemaAutoConfiguration.verificar(jdbc, M4SchemaContract.V013));
        assertEquals("001,002,003,004,005,006,007,008,009,013", jdbc.queryForObject(
                "SELECT string_agg(version, ',' ORDER BY installed_rank) FROM flyway_schema_history WHERE success", String.class));
        assertEquals(1, cantidad("SELECT count(*) FROM \"Rol\" WHERE \"nombre\"='ADMINISTRADOR'"));
        assertEquals(1, cantidad("SELECT count(*) FROM \"Rol\" WHERE \"nombre\"='DOCENTE'"));
        assertEquals(0, marcadores());
    }

    @Test void primeraInicializacionCreaAdministradorActivoAutenticableYMarcador() throws Exception {
        var r = servicio.inicializar("Admin Inicial", "Admin.Inicial@Prisaly.Test", CLAVE, "ARRANQUE");

        assertEquals(Estado.INICIALIZADO, r.estado());
        assertEquals(1, administradores());
        assertEquals(1, marcadores());
        assertEquals(r.idUsuarioAdministrador(), jdbc.queryForObject(
                "SELECT \"idUsuarioAdministrador\" FROM \"InicializacionAdministrador\"", UUID.class));
        assertEquals("ARRANQUE", jdbc.queryForObject("SELECT \"origen\" FROM \"InicializacionAdministrador\"", String.class));
        String hash = jdbc.queryForObject("SELECT \"passwordHash\" FROM \"Usuario\" WHERE \"idUsuario\"=?", String.class,
                r.idUsuarioAdministrador());
        assertTrue(hash.startsWith("$2"), "BCrypt");
        assertFalse(hash.contains(CLAVE));
        assertEquals(0, cantidad("SELECT count(*) FROM \"UsuarioPermiso\""), "no automatic grants");
        String token = login("admin.inicial@prisaly.test", CLAVE);
        assertEquals(200, send("GET", "/v1/admin/roles", token, null).statusCode());
    }

    // P5-I1
    @Test void falloDespuesDeCrearElUsuarioRevierteUsuarioYMarcador() {
        InicializacionAdministradorPort falla = new InicializacionAdministradorPort() {
            @Override public void bloquear() { marcador.bloquear(); }
            @Override public boolean marcadorExiste() { return marcador.marcadorExiste(); }
            @Override public void registrarMarcador(UUID id, String origen) {
                marcador.registrarMarcador(id, origen);
                throw new IllegalStateException("fallo simulado despues de escribir el marcador");
            }
        };
        var conFallo = new InicializacionAdministradorService(falla, roles, catalogo);

        assertThrows(IllegalStateException.class, () -> new TransactionTemplate(tx).executeWithoutResult(
                s -> conFallo.inicializar("Admin", "admin.rollback@prisaly.test", CLAVE, "ARRANQUE")));

        assertEquals(0, administradores());
        assertEquals(0, marcadores());
        assertEquals(0, cantidad("SELECT count(*) FROM \"Usuario\" WHERE \"correo\"='admin.rollback@prisaly.test'"));
        assertEquals(Estado.INICIALIZADO,
                servicio.inicializar("Admin", "admin.rollback@prisaly.test", CLAVE, "ARRANQUE").estado());
    }

    // P5-I2
    @Test void ejecucionesConcurrentesDejanUnSoloAdministradorYUnSoloMarcador() throws Exception {
        int hilos = 6;
        var inicio = new CountDownLatch(1);
        var pool = Executors.newFixedThreadPool(hilos);
        try {
            List<Future<InicializacionAdministradorService.Resultado>> futuros = new ArrayList<>();
            for (int i = 0; i < hilos; i++) {
                String correo = "admin.concurrente." + i + "@prisaly.test";
                Callable<InicializacionAdministradorService.Resultado> tarea = () -> {
                    inicio.await();
                    return servicio.inicializar("Admin " + correo, correo, CLAVE, "ARRANQUE");
                };
                futuros.add(pool.submit(tarea));
            }
            inicio.countDown();
            int inicializados = 0, sinCambios = 0;
            for (var f : futuros) {
                var estado = f.get(60, TimeUnit.SECONDS).estado();
                if (estado == Estado.INICIALIZADO) inicializados++; else sinCambios++;
            }
            assertEquals(1, inicializados);
            assertEquals(hilos - 1, sinCambios);
        } finally {
            pool.shutdownNow();
        }
        assertEquals(1, administradores());
        assertEquals(1, marcadores());
    }

    // P5-I3 and P5-I4
    @Test void reinicioYDesactivacionNoReactivanElProcedimiento() {
        UUID primero = servicio.inicializar("Admin", "admin.unico@prisaly.test", CLAVE, "ARRANQUE").idUsuarioAdministrador();

        var segundo = servicio.inicializar("Otro", "admin.otro@prisaly.test", CLAVE, "ARRANQUE");
        assertEquals(Estado.YA_REALIZADA, segundo.estado());
        assertNull(segundo.idUsuarioAdministrador());

        jdbc.update("UPDATE \"Usuario\" SET \"estado\"='Inactivo' WHERE \"idUsuario\"=?", primero);
        assertEquals(Estado.YA_REALIZADA, servicio.inicializar("Otro", "admin.otro@prisaly.test", CLAVE, "ARRANQUE").estado());

        assertEquals(1, administradores());
        assertEquals(1, marcadores());
        assertEquals(0, cantidad("SELECT count(*) FROM \"Usuario\" WHERE \"correo\"='admin.otro@prisaly.test'"));
    }

    // P5-I5
    @Test void marcadorInmutableYAdministradorNoBorrable() {
        UUID id = servicio.inicializar("Admin", "admin.inmutable@prisaly.test", CLAVE, "ARRANQUE").idUsuarioAdministrador();

        for (String sql : List.of(
                "UPDATE \"InicializacionAdministrador\" SET \"origen\"='MANUAL'",
                "DELETE FROM \"InicializacionAdministrador\"",
                "TRUNCATE \"InicializacionAdministrador\"")) {
            var ex = assertThrows(DataAccessException.class, () -> jdbc.execute(sql), sql);
            assertEquals("23514", sqlState(ex), sql);
            assertTrue(ex.getMostSpecificCause().getMessage().contains("M1-INICIALIZACION-INMUTABLE"), sql);
        }
        var segundo = assertThrows(DataAccessException.class, () -> jdbc.update(
                "INSERT INTO \"InicializacionAdministrador\" (\"idUsuarioAdministrador\",\"origen\") VALUES (?, 'MANUAL')", id));
        assertEquals("23505", sqlState(segundo));
        var otraFila = assertThrows(DataAccessException.class, () -> jdbc.update(
                "INSERT INTO \"InicializacionAdministrador\" (\"idInicializacion\",\"idUsuarioAdministrador\",\"origen\") VALUES (2, ?, 'MANUAL')", id));
        assertEquals("23514", sqlState(otraFila));
        var borrado = assertThrows(DataAccessException.class,
                () -> jdbc.update("DELETE FROM \"Usuario\" WHERE \"idUsuario\"=?", id));
        assertEquals("23503", sqlState(borrado));
        assertEquals(1, marcadores());
        assertEquals(1, administradores());
    }

    // DDL refinement (DC-P5-FASE-B-01 §6): with the marker present a cascaded TRUNCATE is rejected too; on an empty
    // marker table the cleanup of a disposable database still works.
    @Test void truncateEnCascadaSoloSePermiteSinMarcador() {
        var ex = assertThrows(DataAccessException.class, () -> {
            servicio.inicializar("Admin", "admin.cascada@prisaly.test", CLAVE, "ARRANQUE");
            jdbc.execute("TRUNCATE TABLE \"Rol\" RESTART IDENTITY CASCADE");
        });
        assertEquals("23514", sqlState(ex));
        assertEquals(1, marcadores());
        assertEquals(1, administradores());
    }

    @Test void truncateEnCascadaConMarcadorVacioLimpiaLaBaseEfimera() {
        assertDoesNotThrow(() -> jdbc.execute("TRUNCATE TABLE \"Rol\" RESTART IDENTITY CASCADE"));
        assertEquals(0, cantidad("SELECT count(*) FROM \"Rol\""));
        assertEquals(0, marcadores());
    }

    // P5-U5 against the real catalog (D-03 c): the role is never created by the procedure.
    @Test void sinRolAdministradorDePd01NoCreaRolUsuarioNiMarcador() {
        jdbc.update("DELETE FROM \"Rol\" WHERE \"nombre\"='ADMINISTRADOR'");

        var ex = assertThrows(IdentityDomainException.class,
                () -> servicio.inicializar("Admin", "admin.sinrol@prisaly.test", CLAVE, "ARRANQUE"));

        assertEquals(InicializacionAdministradorService.CODE_ADMIN_ROLE_NOT_PROVISIONED, ex.code());
        assertEquals(0, cantidad("SELECT count(*) FROM \"Rol\" WHERE \"nombre\"='ADMINISTRADOR'"));
        assertEquals(0, cantidad("SELECT count(*) FROM \"Usuario\""));
        assertEquals(0, marcadores());
    }

    // P5-U3 against the real policy (DM1-08).
    @Test void contrasenaQueIncumpleLaPoliticaNoPersisteNada() {
        var ex = assertThrows(IdentityDomainException.class,
                () -> servicio.inicializar("Admin", "admin.politica@prisaly.test", "corta", "ARRANQUE"));

        assertEquals(PoliticaContrasena.CODE_TOO_SHORT, ex.code());
        assertEquals(0, cantidad("SELECT count(*) FROM \"Usuario\""));
        assertEquals(0, marcadores());
    }

    // Runner end to end over the real database, with the credential read from a file (P5-U4 on real output).
    @Test void runnerConArchivoDeCredencialInicializaUnaVezSinExponerLaClave(CapturedOutput salida) throws Exception {
        Path archivo = dir.resolve("credencial-admin.txt");
        Files.writeString(archivo, CLAVE + "\n", StandardCharsets.UTF_8);
        var runner = new InicializacionAdministradorRunner(servicio, "admin.runner@prisaly.test", "Admin Runner",
                archivo.toString());

        assertTrue(runner.ejecutar().contains("resultado=EXITOSO"));
        assertTrue(runner.ejecutar().contains("resultado=SIN_CAMBIOS"));

        assertEquals(1, administradores());
        assertEquals(1, marcadores());
        assertFalse(salida.getAll().contains(CLAVE));
        login("admin.runner@prisaly.test", CLAVE);
    }

    // D-04: the shipped default rol-docente (DOCENTE) plus PD-01 make the teacher registration work.
    @Test void altaDeDocenteUsaElRolDocenteDePd01SinCredencialNiPermisos() throws Exception {
        servicio.inicializar("Admin", "admin.docente@prisaly.test", CLAVE, "ARRANQUE");
        String token = login("admin.docente@prisaly.test", CLAVE);

        var r = send("POST", "/v1/admin/docentes", token, "{\"nombre\":\"Docente P5\",\"correo\":\"docente.p5@prisaly.test\"}");

        assertEquals(201, r.statusCode(), r.body());
        UUID id = UUID.fromString(json.readTree(r.body()).get("idUsuario").asText());
        assertEquals("DOCENTE", jdbc.queryForObject("SELECT r.\"nombre\" FROM \"Usuario\" u JOIN \"Rol\" r ON r.\"idRol\"=u.\"idRol\" WHERE u.\"idUsuario\"=?", String.class, id));
        assertNull(jdbc.queryForObject("SELECT \"passwordHash\" FROM \"Usuario\" WHERE \"idUsuario\"=?", String.class, id));
        assertEquals(0, cantidad("SELECT count(*) FROM \"UsuarioPermiso\" WHERE \"idUsuario\"=?", id));
        assertFalse(r.body().toLowerCase().contains("password"));
    }
}
