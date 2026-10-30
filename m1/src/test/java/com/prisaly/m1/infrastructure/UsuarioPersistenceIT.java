package com.prisaly.m1.infrastructure;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.prisaly.m1.infrastructure.persistence.RolEntity;
import com.prisaly.m1.infrastructure.persistence.RolRepository;
import com.prisaly.m1.infrastructure.persistence.UsuarioEntity;
import com.prisaly.m1.infrastructure.persistence.UsuarioRepository;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.persistence.autoconfigure.EntityScan;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

@SpringBootTest(classes = UsuarioPersistenceIT.PersistenceTestApp.class)
@Testcontainers
class UsuarioPersistenceIT {

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
        registry.add("spring.flyway.locations", UsuarioPersistenceIT::canonicalFlywayLocation);
    }

    @Autowired
    private RolRepository rolRepository;

    @Autowired
    private UsuarioRepository usuarioRepository;

    @Test
    void persistsUsuarioWithSinglePrincipalRoleAndUniqueEmail() {
        RolEntity docente = rolRepository.save(new RolEntity("Docente", "Docente academico"));
        UsuarioEntity usuario = new UsuarioEntity(
                UUID.randomUUID(),
                docente,
                "Ana Docente",
                "ana.docente@prisaly.test",
                "$2a$10$placeholderHashForPersistenceOnlyxxxxxxxxxxxx",
                "Activo",
                OffsetDateTime.now());
        usuarioRepository.saveAndFlush(usuario);

        assertTrue(usuarioRepository.existsByCorreoIgnoreCase("ana.docente@prisaly.test"));
        UsuarioEntity loaded = usuarioRepository.findByCorreoIgnoreCase("ana.docente@prisaly.test").orElseThrow();
        assertEquals("Docente", loaded.getRol().getNombre());
        assertEquals(docente.getIdRol(), loaded.getRol().getIdRol());
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
    static class PersistenceTestApp {
    }
}
