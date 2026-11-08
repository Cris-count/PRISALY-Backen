package com.prisaly.m1.infrastructure;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.prisaly.m1.application.port.CuentaEstudiantePort;
import com.prisaly.m1.application.service.ActivacionCuentaEstudianteService;
import com.prisaly.m1.application.service.AutenticacionEstudianteService;
import com.prisaly.m1.application.service.SessionStudentAccessService;
import com.prisaly.m1.domain.rule.IdentityDomainException;
import com.prisaly.m1.domain.student.ClaveHmacEstudiante;
import com.prisaly.m1.infrastructure.persistence.CuentaEstudianteJdbcAdapter;
import com.prisaly.m1.infrastructure.security.PasswordHashConfig;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

@SpringBootTest(classes = CuentaEstudiantePostgreSqlIT.App.class)
@Testcontainers
class CuentaEstudiantePostgreSqlIT {

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
        registry.add("spring.flyway.locations", CuentaEstudiantePostgreSqlIT::flyway);
    }

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private CuentaEstudiantePort cuentas;

    @Autowired
    private PasswordEncoder encoder;

    @Test
    void laIdentidadPersistidaSeRevocaYNoCruzaEstudiantes() {
        UUID propio = insertar("propio.pg@prisaly.test");
        UUID ajeno = insertar("ajeno.pg@prisaly.test");
        ClaveHmacEstudiante clave = new ClaveHmacEstudiante("clave-hmac-prueba".getBytes(StandardCharsets.UTF_8));
        var activacion = new ActivacionCuentaEstudianteService(cuentas, encoder, clave);
        var autenticacion = new AutenticacionEstudianteService(cuentas, encoder, clave);
        AtomicReference<String> token = new AtomicReference<>();
        var acceso = new SessionStudentAccessService(cuentas, () -> Optional.ofNullable(token.get()), clave);

        var emitido = activacion.emitir(propio);
        byte[] firma = jdbc.queryForObject(
                "SELECT \"codigoHmac\" FROM \"ActivacionCuentaEstudiante\" WHERE \"challengeId\" = ?",
                byte[].class,
                emitido.challengeId());
        assertFalse(new String(firma, StandardCharsets.UTF_8).contains(emitido.codigo()));
        activacion.activar(emitido.challengeId(), emitido.codigo(), "contrasena-segura");
        assertThrows(IdentityDomainException.class,
                () -> autenticacion.autenticar("propio.pg@prisaly.test", "contrasena-incorrecta"));

        token.set(autenticacion.autenticar("propio.pg@prisaly.test", "contrasena-segura").token());
        assertEquals(propio, acceso.requireCurrentStudent().userId());
        UUID estudiante = jdbc.queryForObject(
                "SELECT \"idEstudiante\" FROM \"Estudiante\" WHERE \"idUsuario\" = ?", UUID.class, propio);
        assertEquals(estudiante, acceso.requireCurrentStudent().studentId());

        var emitidoAjeno = activacion.emitir(ajeno);
        activacion.activar(emitidoAjeno.challengeId(), emitidoAjeno.codigo(), "contrasena-segura");
        token.set(autenticacion.autenticar("ajeno.pg@prisaly.test", "contrasena-segura").token());
        assertEquals(ajeno, acceso.requireCurrentStudent().userId());
        assertFalse(acceso.requireCurrentStudent().userId().equals(propio));

        autenticacion.revocar(token.get());
        assertThrows(IdentityDomainException.class, () -> acceso.requireCurrentStudent());
    }

    private UUID insertar(String correo) {
        Integer rol = jdbc.queryForObject(
                "INSERT INTO \"Rol\" (\"nombre\", \"descripcion\") VALUES (?, ?) RETURNING \"idRol\"",
                Integer.class,
                "Rol-" + correo,
                "Prueba");
        UUID usuario = UUID.randomUUID();
        jdbc.update(
                """
                INSERT INTO "Usuario" ("idUsuario", "idRol", "nombre", "correo", "estado")
                VALUES (?, ?, ?, ?, 'Inactivo')
                """,
                usuario, rol, correo, correo);
        jdbc.update(
                "INSERT INTO \"Estudiante\" (\"idEstudiante\", \"idUsuario\") VALUES (?, ?)",
                UUID.randomUUID(), usuario);
        return usuario;
    }

    static String flyway() {
        Path cwd = Path.of(System.getProperty("user.dir")).toAbsolutePath().normalize();
        for (Path candidate : List.of(
                cwd.resolve("app/src/main/resources/db/migration"),
                cwd.resolve("../app/src/main/resources/db/migration").normalize())) {
            if (Files.exists(candidate.resolve("V011__acceso_estudiante_cuenta.sql"))) {
                return "filesystem:" + candidate;
            }
        }
        throw new IllegalStateException("No se encontró V011 desde " + cwd);
    }

    @SpringBootConfiguration
    @EnableAutoConfiguration
    @Import({CuentaEstudianteJdbcAdapter.class, PasswordHashConfig.class})
    static class App {
    }
}
