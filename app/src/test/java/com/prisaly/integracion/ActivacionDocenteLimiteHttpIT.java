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

/** D-05 (DC-77): the public activation route is limited per origin (429 + Retry-After); the login limiter is separate. */
@SpringBootTest(classes = PrisalyApplication.class, webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class ActivacionDocenteLimiteHttpIT extends ActivacionDocenteItBase {
    static final PostgreSQLContainer postgres = nuevoPostgres();
    static final GenericContainer<?> correo = nuevoMailpit();

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry r) {
        base(r, postgres, correo);
        activacionEncendida(r, 5, 3);
    }

    @Override GenericContainer<?> mailpit() { return correo; }

    @Test
    void tresFallosPorOrigenBloqueanInclusoUnTokenValidoYNoAfectanAlLogin() throws Exception {
        prepararAdministrador();
        String correoDocente = "lim." + UUID.randomUUID() + "@prisaly.test";
        UUID docente = alta(correoDocente);
        String valido = tokenDelCorreo(correoDocente, 1);
        for (int i = 0; i < 3; i++) {
            error(activar(("Z" + i).repeat(21) + "Z", "Clave#Valida-2026"), 400, "ACTIVATION_INVALID");
        }
        var limitada = activar(valido, "Clave#Valida-2026");
        error(limitada, 429, "ACTIVATION_RATE_LIMITED");
        long espera = Long.parseLong(limitada.headers().firstValue("Retry-After").orElseThrow());
        assertTrue(espera > 0 && espera <= 900, "Retry-After within the 15-minute window");
        assertNull(jdbc.queryForObject("SELECT \"passwordHash\" FROM \"Usuario\" WHERE \"idUsuario\"=?", String.class,
                docente), "a limited request is not evaluated");
        assertEquals(1, jdbc.queryForObject("SELECT count(*) FROM \"ActivacionDocente\" WHERE \"idUsuario\"=?"
                + " AND \"consumidoEn\" IS NULL AND \"revocadoEn\" IS NULL", Integer.class, docente));
        // The personnel login keeps its own counters.
        prepararAdministrador();
        assertNotNull(tokenAdmin);
    }
}
