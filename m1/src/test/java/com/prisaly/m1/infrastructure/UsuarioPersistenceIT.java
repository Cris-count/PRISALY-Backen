package com.prisaly.m1.infrastructure;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.prisaly.m1.infrastructure.persistence.RolEntity;
import com.prisaly.m1.infrastructure.persistence.RolRepository;
import com.prisaly.m1.infrastructure.persistence.UsuarioEntity;
import com.prisaly.m1.infrastructure.persistence.UsuarioRepository;
import java.time.OffsetDateTime;
import java.util.UUID;
import javax.sql.DataSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.persistence.autoconfigure.EntityScan;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
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
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "none");
        registry.add("spring.flyway.enabled", () -> "false");
    }

    @Autowired
    private RolRepository rolRepository;

    @Autowired
    private UsuarioRepository usuarioRepository;

    @Autowired
    private DataSource dataSource;

    @BeforeEach
    void createSchema() {
        ResourceDatabasePopulator populator = new ResourceDatabasePopulator();
        populator.addScript(new ClassPathResource("db/m1-bd0052-fixture.sql"));
        populator.execute(dataSource);
    }

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

    @SpringBootConfiguration
    @EnableAutoConfiguration
    @EnableJpaRepositories(basePackages = "com.prisaly.m1.infrastructure.persistence")
    @EntityScan(basePackages = "com.prisaly.m1.infrastructure.persistence")
    static class PersistenceTestApp {
    }
}
