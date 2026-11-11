package com.prisaly.m1.infrastructure;

import com.prisaly.m1.domain.model.Usuario;
import com.prisaly.m1.domain.model.Rol;
import com.prisaly.m1.domain.model.Permiso;
import com.prisaly.m1.domain.model.AsignacionPermiso;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.prisaly.m1.application.service.IdentityCatalogService;
import com.prisaly.m1.api.IdentityDomainException;
import com.prisaly.m1.infrastructure.persistence.PermisoEntity;
import com.prisaly.m1.infrastructure.persistence.PermisoRepository;
import com.prisaly.m1.infrastructure.persistence.RolEntity;
import com.prisaly.m1.infrastructure.persistence.UsuarioEntity;
import com.prisaly.m1.infrastructure.persistence.UsuarioPermisoEntity;
import com.prisaly.m1.infrastructure.persistence.UsuarioPermisoRepository;
import com.prisaly.m1.infrastructure.persistence.UsuarioRepository;
import com.prisaly.m1.infrastructure.security.PasswordHashConfig;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.persistence.autoconfigure.EntityScan;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

@SpringBootTest(classes = IdentityPersistenceIT.PersistenceTestApp.class)
@Testcontainers
@org.junit.jupiter.api.extension.ExtendWith(org.springframework.boot.test.system.OutputCaptureExtension.class)
class IdentityPersistenceIT {

    @Container
    static PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:16.15");

    @DynamicPropertySource
    static void datasourceProps(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "validate");
        registry.add("spring.jpa.properties.hibernate.globally_quoted_identifiers", () -> "true");
        registry.add("spring.flyway.enabled", () -> "true");
        registry.add("spring.flyway.locations", IdentityPersistenceIT::canonicalFlywayLocation);
    }

    @Autowired
    private IdentityCatalogService identityCatalogService;

    @Autowired
    private UsuarioRepository usuarioRepository;

    @Autowired
    private PermisoRepository permisoRepository;

    @Autowired
    private UsuarioPermisoRepository usuarioPermisoRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private org.springframework.jdbc.core.JdbcTemplate jdbc;

    private UUID usuarioFixture() {
        String rol = "Rol-" + UUID.randomUUID().toString().substring(0, 8);
        identityCatalogService.createRole(rol, "Fixture");
        return identityCatalogService.registerUser("Fixture", UUID.randomUUID() + "@prisaly.test", "Fixture#Clave-2026", rol)
                .getIdUsuario();
    }

    private UUID administradorFixture() {
        if (!identityCatalogService.existeRolAdministrador()) {
            identityCatalogService.createRole(IdentityCatalogService.ROL_ADMINISTRADOR, "Fixture");
        }
        return identityCatalogService.registerUser("Admin", UUID.randomUUID() + "@prisaly.test", "Fixture#Clave-2026",
                IdentityCatalogService.ROL_ADMINISTRADOR).getIdUsuario();
    }

    private Integer permisoFixture(String codigo) {
        return permisoRepository.findByCodigo(codigo)
                .map(PermisoEntity::getIdPermiso)
                .orElseGet(() -> identityCatalogService.createPermission(codigo, codigo, null).getIdPermiso());
    }

    private void assertCodigo(String esperado, org.junit.jupiter.api.function.Executable accion) {
        assertEquals(esperado, assertThrows(IdentityDomainException.class, accion).code());
    }

    @Test
    void permisoControladoExigeActorExistenteActivoYAdministrador() {
        UUID usuario = usuarioFixture(), actor = administradorFixture(), noAdmin = usuarioFixture();
        Integer permiso = permisoFixture("CONSULTAR_CASO");
        assertCodigo(IdentityCatalogService.CODE_ACTOR_REQUIRED,
                () -> identityCatalogService.assignPermission(usuario, permiso, null));
        assertCodigo(IdentityCatalogService.CODE_USER_NOT_FOUND,
                () -> identityCatalogService.assignPermission(usuario, permiso, UUID.randomUUID()));
        assertCodigo(IdentityCatalogService.CODE_ACTOR_NOT_ADMIN,
                () -> identityCatalogService.assignPermission(usuario, permiso, noAdmin));
        identityCatalogService.deactivateUser(actor);
        assertCodigo(IdentityCatalogService.CODE_ACTOR_INVALID,
                () -> identityCatalogService.assignPermission(usuario, permiso, actor));
    }

    @Test
    void permisosControladosNuncaSeAsignanACuentaEstudiante() {
        UUID estudiante = usuarioFixture(), actor = administradorFixture();
        jdbc.update("INSERT INTO \"Estudiante\" (\"idEstudiante\",\"idUsuario\") VALUES (?,?)", UUID.randomUUID(), estudiante);
        for (String codigo : new String[] {"CONSULTAR_CASO", "MODIFICAR_CASO"}) {
            assertCodigo(IdentityCatalogService.CODE_STUDENT_NOT_ALLOWED,
                    () -> identityCatalogService.assignPermission(estudiante, permisoFixture(codigo), actor));
        }
        assertEquals(0, jdbc.queryForObject("SELECT count(*) FROM \"UsuarioPermiso\" WHERE \"idUsuario\"=?", Integer.class, estudiante));
    }

    @Test
    void asignacionYRevocacionQuedanAuditadasConActor(CapturedOutput salida) {
        UUID usuario = usuarioFixture(), actor = administradorFixture();
        Integer permiso = permisoFixture("MODIFICAR_CASO");
        identityCatalogService.assignPermission(usuario, permiso, actor);
        assertCodigo(IdentityCatalogService.CODE_ACTOR_REQUIRED,
                () -> identityCatalogService.disablePermissionAssignment(usuario, permiso));
        identityCatalogService.disablePermissionAssignment(usuario, permiso, actor);
        assertEquals(false, jdbc.queryForObject("SELECT \"habilitado\" FROM \"UsuarioPermiso\" WHERE \"idUsuario\"=? AND \"idPermiso\"=?", Boolean.class, usuario, permiso));
        String base = " usuario=" + usuario + " permiso=MODIFICAR_CASO administrador=" + actor + " fecha=";
        assertTrue(salida.getOut().contains("AUDITORIA_PERMISO accion=ASIGNAR" + base), salida.getOut());
        assertTrue(salida.getOut().contains("AUDITORIA_PERMISO accion=REVOCAR" + base), salida.getOut());
        assertTrue(salida.getOut().contains("resultado=EXITOSO"), salida.getOut());
        assertTrue(salida.getOut().contains("resultado=RECHAZADO:" + IdentityCatalogService.CODE_ACTOR_REQUIRED), salida.getOut());
    }

    /**
     * DM1-02 (DC-M1-03) replaces the former S-1 behaviour (asserted by
     * permisoNoControladoConservaElComportamientoAnterior): non-controlled permissions now also require
     * an existing, active administrator to assign or revoke, and every rejection leaves nothing persisted.
     */
    @Test
    void permisoNoControladoTambienExigeAdministrador() {
        UUID usuario = usuarioFixture(), actor = administradorFixture(), noAdmin = usuarioFixture();
        // GESTIONAR_SESION, not CREAR_CASO: persistsPermisoAndUsuarioPermisoCompositeKey creates CREAR_CASO unguarded.
        Integer permiso = permisoFixture("GESTIONAR_SESION");
        assertCodigo(IdentityCatalogService.CODE_ACTOR_REQUIRED,
                () -> identityCatalogService.assignPermission(usuario, permiso, null));
        assertCodigo(IdentityCatalogService.CODE_USER_NOT_FOUND,
                () -> identityCatalogService.assignPermission(usuario, permiso, UUID.randomUUID()));
        assertCodigo(IdentityCatalogService.CODE_ACTOR_NOT_ADMIN,
                () -> identityCatalogService.assignPermission(usuario, permiso, noAdmin));
        assertEquals(0, jdbc.queryForObject("SELECT count(*) FROM \"UsuarioPermiso\" WHERE \"idUsuario\"=?", Integer.class, usuario));
        identityCatalogService.assignPermission(usuario, permiso, actor);
        assertCodigo(IdentityCatalogService.CODE_ACTOR_REQUIRED,
                () -> identityCatalogService.disablePermissionAssignment(usuario, permiso));
        assertCodigo(IdentityCatalogService.CODE_ACTOR_NOT_ADMIN,
                () -> identityCatalogService.disablePermissionAssignment(usuario, permiso, noAdmin));
        assertEquals(true, jdbc.queryForObject("SELECT \"habilitado\" FROM \"UsuarioPermiso\" WHERE \"idUsuario\"=? AND \"idPermiso\"=?", Boolean.class, usuario, permiso));
        identityCatalogService.disablePermissionAssignment(usuario, permiso, actor);
        assertEquals(false, jdbc.queryForObject("SELECT \"habilitado\" FROM \"UsuarioPermiso\" WHERE \"idUsuario\"=? AND \"idPermiso\"=?", Boolean.class, usuario, permiso));
        identityCatalogService.deactivateUser(actor);
        assertCodigo(IdentityCatalogService.CODE_ACTOR_INVALID,
                () -> identityCatalogService.assignPermission(noAdmin, permiso, actor));
    }

    /** DM1-02 keeps PD-01 separation of duties: an administrator never grants itself PUBLICAR/ARCHIVAR. */
    @Test
    void administradorNoSeAutoasignaPermisosOperativos() {
        UUID actor = administradorFixture();
        for (String codigo : new String[] {"PUBLICAR_CASO", "ARCHIVAR_CASO"}) {
            assertCodigo(IdentityCatalogService.CODE_SELF_ASSIGNMENT,
                    () -> identityCatalogService.assignPermission(actor, permisoFixture(codigo), actor));
        }
        assertEquals(0, jdbc.queryForObject("SELECT count(*) FROM \"UsuarioPermiso\" WHERE \"idUsuario\"=?", Integer.class, actor));
    }

    @Test
    void persistsUsuarioWithSinglePrincipalRoleAndUniqueEmail() {
        identityCatalogService.createRole("Docente", "Docente academico");
        Usuario usuario = identityCatalogService.registerUser(
                "Ana Docente",
                "Ana.Docente@prisaly.test",
                "Fixture#Clave-2026-123",
                "Docente");

        assertTrue(usuarioRepository.existsByCorreoIgnoreCase("ana.docente@prisaly.test"));
        UsuarioEntity loaded = usuarioRepository.findByCorreoIgnoreCase("ana.docente@prisaly.test").orElseThrow();
        assertEquals("Docente", loaded.getRol().getNombre());
        assertEquals(usuario.getIdUsuario(), loaded.getIdUsuario());
        assertEquals("ana.docente@prisaly.test", loaded.getCorreo());
    }

    @Test
    void storesPasswordAsBcryptAndMatches() {
        identityCatalogService.createRole("Administrador", "Admin");
        Usuario usuario = identityCatalogService.registerUser(
                "Admin User",
                "admin@prisaly.test",
                "PlainPassword!",
                "Administrador");

        assertTrue(usuario.getPasswordHash().startsWith("$2a$")
                || usuario.getPasswordHash().startsWith("$2b$"));
        assertTrue(passwordEncoder.matches("PlainPassword!", usuario.getPasswordHash()));
        assertFalse(passwordEncoder.matches("wrong", usuario.getPasswordHash()));
    }

    @Test
    void rejectsDuplicateEmailCaseInsensitive() {
        identityCatalogService.createRole("DocenteDup", "Docente");
        identityCatalogService.registerUser("Uno", "dup@prisaly.test", "Fixture#Clave-2026", "DocenteDup");

        IdentityDomainException error = assertThrows(
                IdentityDomainException.class,
                () -> identityCatalogService.registerUser(
                        "Dos", "DUP@prisaly.test", "Fixture#Clave-2026-2", "DocenteDup"));

        assertEquals(IdentityCatalogService.CODE_DUPLICATE_EMAIL, error.code());
    }

    @Test
    void persistsPermisoAndUsuarioPermisoCompositeKey() {
        Rol adminRole = identityCatalogService.createRole("AdminPerm", "Admin");
        Permiso permiso = identityCatalogService.createPermission(
                "CREAR_CASO", "Crear caso", "Alta de caso clinico");
        // DM1-02: the assigner must hold the ADMINISTRADOR role (setup only; assertions unchanged).
        if (!identityCatalogService.existeRolAdministrador()) {
            identityCatalogService.createRole(IdentityCatalogService.ROL_ADMINISTRADOR, "Fixture");
        }
        Usuario admin = identityCatalogService.registerUser(
                "Admin Asignador",
                "asignador@prisaly.test",
                "Fixture#Clave-2026",
                IdentityCatalogService.ROL_ADMINISTRADOR);
        Usuario docente = identityCatalogService.registerUser(
                "Docente Perm",
                "docente.perm@prisaly.test",
                "Fixture#Clave-2026",
                "AdminPerm");

        AsignacionPermiso assignment = identityCatalogService.assignPermission(
                docente.getIdUsuario(),
                permiso.getIdPermiso(),
                admin.getIdUsuario());

        assertEquals(docente.getIdUsuario(), assignment.getIdUsuario());
        assertEquals(permiso.getIdPermiso(), assignment.getIdPermiso());
        assertTrue(assignment.isHabilitado());
        assertEquals(admin.getIdUsuario(), assignment.getIdAdministradorAsignador());

        List<UsuarioPermisoEntity> enabled =
                usuarioPermisoRepository.findByIdUsuarioAndHabilitadoTrue(docente.getIdUsuario());
        assertEquals(1, enabled.size());
        assertEquals("CREAR_CASO", enabled.getFirst().getPermiso().getCodigo());
        assertEquals(adminRole.getNombre(), docente.getRol().getNombre());
    }

    @Test
    void rejectsDuplicateUsuarioPermisoAssignment() {
        identityCatalogService.createRole("RolAsig", "Rol");
        Permiso permiso = identityCatalogService.createPermission(
                "EDITAR_CASO", "Editar caso", null);
        Usuario user = identityCatalogService.registerUser(
                "User Asig", "user.asig@prisaly.test", "Fixture#Clave-2026", "RolAsig");

        UUID actor = administradorFixture(); // DM1-02: setup only
        identityCatalogService.assignPermission(user.getIdUsuario(), permiso.getIdPermiso(), actor);

        IdentityDomainException error = assertThrows(
                IdentityDomainException.class,
                () -> identityCatalogService.assignPermission(
                        user.getIdUsuario(), permiso.getIdPermiso(), actor));

        assertEquals(IdentityCatalogService.CODE_DUPLICATE_ASSIGNMENT, error.code());
    }

    @Test
    void disablePermissionAssignmentIsTransactional() {
        identityCatalogService.createRole("RolDisable", "Rol");
        Permiso permiso = identityCatalogService.createPermission(
                "VER_REPORTE", "Ver reporte", "Consulta");
        Usuario user = identityCatalogService.registerUser(
                "User Disable", "user.disable@prisaly.test", "Fixture#Clave-2026", "RolDisable");
        UUID actor = administradorFixture(); // DM1-02: setup only
        identityCatalogService.assignPermission(user.getIdUsuario(), permiso.getIdPermiso(), actor);

        identityCatalogService.disablePermissionAssignment(user.getIdUsuario(), permiso.getIdPermiso(), actor);

        assertTrue(usuarioPermisoRepository
                .findByIdUsuarioAndHabilitadoTrue(user.getIdUsuario())
                .isEmpty());
        assertFalse(usuarioPermisoRepository
                .findByIdUsuarioAndIdPermiso(user.getIdUsuario(), permiso.getIdPermiso())
                .orElseThrow()
                .isHabilitado());
        assertTrue(permisoRepository.findByCodigo("VER_REPORTE").isPresent());
    }

    static String canonicalFlywayLocation() {
        Path cwd = Path.of(System.getProperty("user.dir")).toAbsolutePath().normalize();
        List<Path> candidates = List.of(
                cwd.resolve("app/src/main/resources/db/migration"),
                cwd.resolve("../app/src/main/resources/db/migration").normalize());
        for (Path candidate : candidates) {
            if (Files.isDirectory(candidate) && Files.exists(candidate.resolve("V001__bd0052_estructura.sql"))) {
                return "filesystem:" + candidate;
            }
        }
        throw new IllegalStateException(
                "Canonical Flyway directory with V001–V009 not found from user.dir=" + cwd);
    }

    @SpringBootConfiguration
    @EnableAutoConfiguration
    @EnableJpaRepositories(basePackages = "com.prisaly.m1.infrastructure.persistence")
    @EntityScan(basePackages = "com.prisaly.m1.infrastructure.persistence")
    @Import({IdentityCatalogService.class, PasswordHashConfig.class,
            com.prisaly.m1.application.service.GestionPermisosService.class,
            com.prisaly.m1.infrastructure.persistence.IdentidadJpaAdapter.class,
            com.prisaly.m1.infrastructure.security.AuditoriaPermisosLog.class,
            // DM1-08 (DC-M1-04): the catalog validates passwords against the blocklist.
            com.prisaly.m1.infrastructure.security.ContrasenasBloqueadasClasspath.class})
    static class PersistenceTestApp {
    }
}
