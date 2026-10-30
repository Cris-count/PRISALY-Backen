package com.prisaly.m1.infrastructure;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.prisaly.m1.application.service.IdentityCatalogService;
import com.prisaly.m1.domain.rule.IdentityDomainException;
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
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

@SpringBootTest(classes = IdentityPersistenceIT.PersistenceTestApp.class)
@Testcontainers
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

    @Test
    void persistsUsuarioWithSinglePrincipalRoleAndUniqueEmail() {
        identityCatalogService.createRole("Docente", "Docente academico");
        UsuarioEntity usuario = identityCatalogService.registerUser(
                "Ana Docente",
                "Ana.Docente@prisaly.test",
                "Secret#123",
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
        UsuarioEntity usuario = identityCatalogService.registerUser(
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
        identityCatalogService.registerUser("Uno", "dup@prisaly.test", "Secret#1", "DocenteDup");

        IdentityDomainException error = assertThrows(
                IdentityDomainException.class,
                () -> identityCatalogService.registerUser(
                        "Dos", "DUP@prisaly.test", "Secret#2", "DocenteDup"));

        assertEquals(IdentityCatalogService.CODE_DUPLICATE_EMAIL, error.code());
    }

    @Test
    void persistsPermisoAndUsuarioPermisoCompositeKey() {
        RolEntity adminRole = identityCatalogService.createRole("AdminPerm", "Admin");
        PermisoEntity permiso = identityCatalogService.createPermission(
                "CREAR_CASO", "Crear caso", "Alta de caso clinico");
        UsuarioEntity admin = identityCatalogService.registerUser(
                "Admin Asignador",
                "asignador@prisaly.test",
                "Secret#1",
                "AdminPerm");
        UsuarioEntity docente = identityCatalogService.registerUser(
                "Docente Perm",
                "docente.perm@prisaly.test",
                "Secret#1",
                "AdminPerm");

        UsuarioPermisoEntity assignment = identityCatalogService.assignPermission(
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
        PermisoEntity permiso = identityCatalogService.createPermission(
                "EDITAR_CASO", "Editar caso", null);
        UsuarioEntity user = identityCatalogService.registerUser(
                "User Asig", "user.asig@prisaly.test", "Secret#1", "RolAsig");

        identityCatalogService.assignPermission(user.getIdUsuario(), permiso.getIdPermiso(), null);

        IdentityDomainException error = assertThrows(
                IdentityDomainException.class,
                () -> identityCatalogService.assignPermission(
                        user.getIdUsuario(), permiso.getIdPermiso(), null));

        assertEquals(IdentityCatalogService.CODE_DUPLICATE_ASSIGNMENT, error.code());
    }

    @Test
    void disablePermissionAssignmentIsTransactional() {
        identityCatalogService.createRole("RolDisable", "Rol");
        PermisoEntity permiso = identityCatalogService.createPermission(
                "VER_REPORTE", "Ver reporte", "Consulta");
        UsuarioEntity user = identityCatalogService.registerUser(
                "User Disable", "user.disable@prisaly.test", "Secret#1", "RolDisable");
        identityCatalogService.assignPermission(user.getIdUsuario(), permiso.getIdPermiso(), null);

        identityCatalogService.disablePermissionAssignment(user.getIdUsuario(), permiso.getIdPermiso());

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
    @Import({IdentityCatalogService.class, PasswordHashConfig.class})
    static class PersistenceTestApp {
    }
}
