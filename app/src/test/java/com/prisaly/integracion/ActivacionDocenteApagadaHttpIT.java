package com.prisaly.integracion;

import static org.junit.jupiter.api.Assertions.*;

import com.prisaly.PrisalyApplication;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.postgresql.PostgreSQLContainer;

/**
 * D-05 (DC-77) switch OFF (default) with the official schema only (no V020): behaviour is exactly the previous one.
 * No routes, no invitation mail, no table, and the public activation route is not opened.
 */
@SpringBootTest(classes = PrisalyApplication.class, webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class ActivacionDocenteApagadaHttpIT extends ActivacionDocenteItBase {
    static final PostgreSQLContainer postgres = nuevoPostgres();
    static final GenericContainer<?> correo = nuevoMailpit();

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry r) {
        base(r, postgres, correo);
    }

    @Override GenericContainer<?> mailpit() { return correo; }

    @Test
    void sinInterruptorNoHayActivacionNiCorreoNiTabla() throws Exception {
        prepararAdministrador();
        String correoDocente = "off." + UUID.randomUUID() + "@prisaly.test";
        UUID docente = alta(correoDocente);
        Thread.sleep(500);
        assertEquals(0, correosPara(correoDocente), "no invitation is sent");
        assertNull(jdbc.queryForObject("SELECT to_regclass('\"ActivacionDocente\"')::text", String.class));
        error(activar("A".repeat(43), "Clave#Valida-2026"), 401, "UNAUTHENTICATED");
        assertEquals(404, send("POST", "/v1/admin/docentes/" + docente + "/activacion", tokenAdmin, null).statusCode());
        assertEquals(404, send("DELETE", "/v1/admin/docentes/" + docente + "/activacion", tokenAdmin, null).statusCode());
        assertEquals(404, send("POST", "/v1/personal/activar", tokenAdmin,
                "{\"token\":\"" + "A".repeat(43) + "\",\"password\":\"Clave#Valida-2026\"}").statusCode());
    }
}
