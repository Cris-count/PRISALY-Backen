package com.prisaly.integracion;

import com.prisaly.m1.domain.model.Usuario;
import static org.junit.jupiter.api.Assertions.*;

import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.core.rolling.RollingFileAppender;
import ch.qos.logback.core.rolling.TimeBasedRollingPolicy;
import com.prisaly.PrisalyApplication;
import com.prisaly.config.VerificadorRolAdministrador;
import com.prisaly.m1.application.service.IdentityCatalogService;
import com.prisaly.m1.api.IdentityDomainException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.logging.LoggingInitializationContext;
import org.springframework.boot.logging.LoggingSystem;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.postgresql.PostgreSQLContainer;

/** PD-01 part two: assignment, revocation, insufficient permissions, inactive accounts, durable audit. */
@SpringBootTest(classes = PrisalyApplication.class)
class AuditoriaPermisosPersistenciaIT {
    static final PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:16.15");
    static final Path ARCHIVO = Path.of("target/auditoria-it/auditoria-permisos.log");
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
    }

    /** Logback initializes once per JVM; this suite needs the productive file with its own values. */
    @BeforeAll static void configurarRegistro() {
        var entorno = new org.springframework.mock.env.MockEnvironment()
                .withProperty("prisaly.auditoria.permisos.directorio", "target/auditoria-it")
                .withProperty("prisaly.auditoria.permisos.retencion-dias", "30");
        var sistema = LoggingSystem.get(AuditoriaPermisosPersistenciaIT.class.getClassLoader());
        sistema.cleanUp();
        sistema.beforeInitialize();
        sistema.initialize(new LoggingInitializationContext(entorno), "classpath:logback-spring.xml", null);
    }

    @Autowired IdentityCatalogService catalog;
    @Autowired JdbcTemplate jdbc;
    @Autowired VerificadorRolAdministrador verificador;
    UUID admin;
    Integer consultar;

    @BeforeEach void inicializar() throws Exception {
        jdbc.execute(Files.readString(Path.of("../docs/provision/pd01-inicializacion.sql")));
        admin = cuenta(IdentityCatalogService.ROL_ADMINISTRADOR);
        consultar = jdbc.queryForObject("SELECT \"idPermiso\" FROM \"Permiso\" WHERE \"codigo\"='CONSULTAR_CASO'", Integer.class);
    }

    private UUID cuenta(String rol) {
        Usuario u = catalog.registerUser("Cuenta", UUID.randomUUID() + "@prisaly.test", "Fixture#Clave-2026", rol);
        return u.getIdUsuario();
    }
    private UUID docente() {
        String rol = "Rol-" + UUID.randomUUID().toString().substring(0, 8);
        catalog.createRole(rol, "Docente");
        return cuenta(rol);
    }
    private String registro() throws Exception { return Files.readString(ARCHIVO); }
    private void rechazo(String codigo, org.junit.jupiter.api.function.Executable accion) {
        assertEquals(codigo, assertThrows(IdentityDomainException.class, accion).code());
    }

    @Test void asignacionYRevocacionSeAplicanYPersistenEnElArchivo() throws Exception {
        UUID docente = docente();
        catalog.assignPermission(docente, consultar, admin);
        assertEquals(true, jdbc.queryForObject("SELECT \"habilitado\" FROM \"UsuarioPermiso\" WHERE \"idUsuario\"=? AND \"idPermiso\"=?", Boolean.class, docente, consultar));
        assertEquals(admin, jdbc.queryForObject("SELECT \"idAdministradorAsignador\" FROM \"UsuarioPermiso\" WHERE \"idUsuario\"=? AND \"idPermiso\"=?", UUID.class, docente, consultar));
        catalog.disablePermissionAssignment(docente, consultar, admin);
        assertEquals(false, jdbc.queryForObject("SELECT \"habilitado\" FROM \"UsuarioPermiso\" WHERE \"idUsuario\"=? AND \"idPermiso\"=?", Boolean.class, docente, consultar));
        String base = "usuario=" + docente + " permiso=CONSULTAR_CASO administrador=" + admin + " fecha=";
        String texto = registro();
        assertTrue(texto.lines().anyMatch(l -> l.contains("accion=ASIGNAR " + base) && l.endsWith("resultado=EXITOSO")), texto);
        assertTrue(texto.lines().anyMatch(l -> l.contains("accion=REVOCAR " + base) && l.endsWith("resultado=EXITOSO")), texto);
    }

    @Test void actorSinRolAdministradorSeRechazaSinCambiosYQuedaRegistrado() throws Exception {
        UUID docente = docente(), noAdmin = docente();
        rechazo(IdentityCatalogService.CODE_ACTOR_NOT_ADMIN, () -> catalog.assignPermission(docente, consultar, noAdmin));
        assertEquals(0, jdbc.queryForObject("SELECT count(*) FROM \"UsuarioPermiso\" WHERE \"idUsuario\"=?", Integer.class, docente));
        catalog.assignPermission(docente, consultar, admin);
        rechazo(IdentityCatalogService.CODE_ACTOR_NOT_ADMIN, () -> catalog.disablePermissionAssignment(docente, consultar, noAdmin));
        assertEquals(true, jdbc.queryForObject("SELECT \"habilitado\" FROM \"UsuarioPermiso\" WHERE \"idUsuario\"=? AND \"idPermiso\"=?", Boolean.class, docente, consultar));
        String texto = registro();
        String fin = "administrador=" + noAdmin;
        assertTrue(texto.lines().anyMatch(l -> l.contains("accion=ASIGNAR") && l.contains(fin) && l.endsWith("resultado=RECHAZADO:" + IdentityCatalogService.CODE_ACTOR_NOT_ADMIN)), texto);
        assertTrue(texto.lines().anyMatch(l -> l.contains("accion=REVOCAR") && l.contains(fin) && l.endsWith("resultado=RECHAZADO:" + IdentityCatalogService.CODE_ACTOR_NOT_ADMIN)), texto);
    }

    @Test void administradorInactivoNoAsignaNiRevoca() throws Exception {
        UUID docente = docente(), inactivo = cuenta(IdentityCatalogService.ROL_ADMINISTRADOR);
        catalog.assignPermission(docente, consultar, admin);
        catalog.deactivateUser(inactivo);
        rechazo(IdentityCatalogService.CODE_ACTOR_INVALID, () -> catalog.disablePermissionAssignment(docente, consultar, inactivo));
        assertEquals(true, jdbc.queryForObject("SELECT \"habilitado\" FROM \"UsuarioPermiso\" WHERE \"idUsuario\"=? AND \"idPermiso\"=?", Boolean.class, docente, consultar));
        assertTrue(registro().contains("administrador=" + inactivo));
    }

    @Test void sinActorSeRechazaYSeRegistra() throws Exception {
        UUID docente = docente();
        rechazo(IdentityCatalogService.CODE_ACTOR_REQUIRED, () -> catalog.assignPermission(docente, consultar, null));
        assertTrue(registro().lines().anyMatch(l -> l.contains("usuario=" + docente) && l.endsWith("resultado=RECHAZADO:" + IdentityCatalogService.CODE_ACTOR_REQUIRED)));
    }

    @Test void elRegistroNoContieneSecretosNiDatosPersonales() throws Exception {
        UUID docente = docente();
        catalog.assignPermission(docente, consultar, admin);
        String texto = registro();
        String hash = jdbc.queryForObject("SELECT \"passwordHash\" FROM \"Usuario\" WHERE \"idUsuario\"=?", String.class, docente);
        for (String prohibido : new String[] {"Fixture#Clave-2026", hash, "@prisaly.test", "Bearer", "password", "token"}) {
            assertFalse(texto.contains(prohibido), prohibido);
        }
    }

    @Test void retencionYRotacionSonConfigurablesYElRegistroEsExclusivo() {
        LoggerContext contexto = (LoggerContext) LoggerFactory.getILoggerFactory();
        var logger = contexto.getLogger(IdentityCatalogService.LOGGER_AUDITORIA);
        assertFalse(logger.isAdditive());
        var appender = assertInstanceOf(RollingFileAppender.class, logger.getAppender("AUDITORIA_PERMISOS"));
        var politica = assertInstanceOf(TimeBasedRollingPolicy.class, appender.getRollingPolicy());
        assertEquals(30, politica.getMaxHistory());
        assertTrue(politica.getFileNamePattern().endsWith("auditoria-permisos.%d{yyyy-MM-dd}.log.gz"), politica.getFileNamePattern());
    }

    private Integer permisoPorCodigo(String codigo) {
        return jdbc.queryForObject("SELECT \"idPermiso\" FROM \"Permiso\" WHERE \"codigo\"=?", Integer.class, codigo);
    }

    @ParameterizedTest
    @ValueSource(strings = {"PUBLICAR_CASO", "ARCHIVAR_CASO"})
    void f6AsignacionYRevocacionPersistenConTodosLosCampos(String codigo) throws Exception {
        UUID docente = docente();
        Integer permiso = permisoPorCodigo(codigo);
        catalog.assignPermission(docente, permiso, admin);
        catalog.disablePermissionAssignment(docente, permiso, admin);
        assertEquals(false, jdbc.queryForObject("SELECT \"habilitado\" FROM \"UsuarioPermiso\" WHERE \"idUsuario\"=? AND \"idPermiso\"=?", Boolean.class, docente, permiso));
        String base = "usuario=" + docente + " permiso=" + codigo + " administrador=" + admin + " fecha=";
        String texto = registro();
        assertTrue(texto.lines().anyMatch(l -> l.contains("accion=ASIGNAR " + base) && l.endsWith("resultado=EXITOSO")), texto);
        assertTrue(texto.lines().anyMatch(l -> l.contains("accion=REVOCAR " + base) && l.endsWith("resultado=EXITOSO")), texto);
    }

    @ParameterizedTest
    @ValueSource(strings = {"PUBLICAR_CASO", "ARCHIVAR_CASO"})
    void f6ActorNuloNoAutorizadoEInactivoSeRechazanYRegistran(String codigo) throws Exception {
        UUID docente = docente(), noAdmin = docente(), inactivo = cuenta(IdentityCatalogService.ROL_ADMINISTRADOR);
        catalog.deactivateUser(inactivo);
        Integer permiso = permisoPorCodigo(codigo);
        rechazo(IdentityCatalogService.CODE_ACTOR_REQUIRED, () -> catalog.assignPermission(docente, permiso, null));
        rechazo(IdentityCatalogService.CODE_ACTOR_NOT_ADMIN, () -> catalog.assignPermission(docente, permiso, noAdmin));
        rechazo(IdentityCatalogService.CODE_ACTOR_INVALID, () -> catalog.assignPermission(docente, permiso, inactivo));
        assertEquals(0, jdbc.queryForObject("SELECT count(*) FROM \"UsuarioPermiso\" WHERE \"idUsuario\"=?", Integer.class, docente));
        catalog.assignPermission(docente, permiso, admin);
        rechazo(IdentityCatalogService.CODE_ACTOR_REQUIRED, () -> catalog.disablePermissionAssignment(docente, permiso));
        rechazo(IdentityCatalogService.CODE_ACTOR_NOT_ADMIN, () -> catalog.disablePermissionAssignment(docente, permiso, noAdmin));
        assertEquals(true, jdbc.queryForObject("SELECT \"habilitado\" FROM \"UsuarioPermiso\" WHERE \"idUsuario\"=? AND \"idPermiso\"=?", Boolean.class, docente, permiso));
        assertTrue(registro().lines().anyMatch(l -> l.contains("usuario=" + docente) && l.contains("permiso=" + codigo)
                && l.endsWith("resultado=RECHAZADO:" + IdentityCatalogService.CODE_ACTOR_REQUIRED)));
    }

    @ParameterizedTest
    @ValueSource(strings = {"PUBLICAR_CASO", "ARCHIVAR_CASO"})
    void f6EstudianteNoRecibeElPermiso(String codigo) throws Exception {
        UUID estudiante = docente();
        jdbc.update("INSERT INTO \"Estudiante\" (\"idEstudiante\",\"idUsuario\") VALUES (?,?)", UUID.randomUUID(), estudiante);
        rechazo(IdentityCatalogService.CODE_STUDENT_NOT_ALLOWED, () -> catalog.assignPermission(estudiante, permisoPorCodigo(codigo), admin));
        assertEquals(0, jdbc.queryForObject("SELECT count(*) FROM \"UsuarioPermiso\" WHERE \"idUsuario\"=?", Integer.class, estudiante));
        assertTrue(registro().lines().anyMatch(l -> l.contains("usuario=" + estudiante) && l.endsWith("resultado=RECHAZADO:" + IdentityCatalogService.CODE_STUDENT_NOT_ALLOWED)));
    }

    @ParameterizedTest
    @ValueSource(strings = {"PUBLICAR_CASO", "ARCHIVAR_CASO"})
    void f6AsignarYEjecutarSeMantienenSeparados(String codigo) throws Exception {
        rechazo(IdentityCatalogService.CODE_SELF_ASSIGNMENT, () -> catalog.assignPermission(admin, permisoPorCodigo(codigo), admin));
        assertEquals(0, jdbc.queryForObject("SELECT count(*) FROM \"UsuarioPermiso\" WHERE \"idUsuario\"=?", Integer.class, admin));
    }

    @Test void laInicializacionNoConcedePermisosAlAdministrador() throws Exception {
        jdbc.execute(Files.readString(Path.of("../docs/provision/pd01-inicializacion.sql")));
        assertEquals(4, jdbc.queryForObject("SELECT count(*) FROM \"Permiso\" WHERE \"codigo\" IN ('CONSULTAR_CASO','MODIFICAR_CASO','PUBLICAR_CASO','ARCHIVAR_CASO')", Integer.class));
        assertEquals(0, jdbc.queryForObject("SELECT count(*) FROM \"UsuarioPermiso\" WHERE \"idUsuario\"=?", Integer.class, admin));
    }

    /** DM1-03 (DC-M1-03): CREAR_CASO and GESTIONAR_SESION are provisioned idempotently and granted to nobody. */
    @Test void laInicializacionAprovisionaPermisosOperativosSinAsignarlos() throws Exception {
        String script = Files.readString(Path.of("../docs/provision/pd01-inicializacion.sql"));
        Integer asignacionesAntes = jdbc.queryForObject("SELECT count(*) FROM \"UsuarioPermiso\"", Integer.class);
        jdbc.execute(script);
        jdbc.execute(script);
        for (String codigo : new String[] {"CREAR_CASO", "GESTIONAR_SESION"}) {
            assertEquals(1, jdbc.queryForObject("SELECT count(*) FROM \"Permiso\" WHERE \"codigo\"=?", Integer.class, codigo));
        }
        assertEquals(asignacionesAntes, jdbc.queryForObject("SELECT count(*) FROM \"UsuarioPermiso\"", Integer.class));
    }

    @Test void laInicializacionEsIdempotenteYElRolQuedaVerificado() throws Exception {
        String script = Files.readString(Path.of("../docs/provision/pd01-inicializacion.sql"));
        jdbc.execute(script);
        jdbc.execute(script);
        assertEquals(1, jdbc.queryForObject("SELECT count(*) FROM \"Rol\" WHERE \"nombre\"='ADMINISTRADOR'", Integer.class));
        assertEquals(2, jdbc.queryForObject("SELECT count(*) FROM \"Permiso\" WHERE \"codigo\" IN ('CONSULTAR_CASO','MODIFICAR_CASO')", Integer.class));
        assertTrue(verificador.verificar());
    }
}
