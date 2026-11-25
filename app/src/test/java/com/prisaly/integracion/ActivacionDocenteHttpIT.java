package com.prisaly.integracion;

import static org.junit.jupiter.api.Assertions.*;

import com.prisaly.PrisalyApplication;
import com.prisaly.m1.application.service.ActivacionDocenteService;
import java.net.http.HttpResponse;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataAccessException;
import org.springframework.security.crypto.bcrypt.BCrypt;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.postgresql.PostgreSQLContainer;

/**
 * D-05 (DC-BE-D05-AUTORIZACION-QA-77) over the real server, PostgreSQL 16.15 with the candidate V020 and Mailpit:
 * invitation on teacher creation, one-time token, DM1-08 without consuming, re-issue and explicit revocation, expiry,
 * administrator-only issue, anti-enumeration, 8 concurrent activations (one wins) and the schema's guards.
 */
@SpringBootTest(classes = PrisalyApplication.class, webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class ActivacionDocenteHttpIT extends ActivacionDocenteItBase {
    static final PostgreSQLContainer postgres = nuevoPostgres();
    static final GenericContainer<?> correo = nuevoMailpit();
    static final String CLAVE = "Docente#Activada-2026";

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry r) {
        base(r, postgres, correo);
        // Limits exercised in ActivacionDocenteLimiteHttpIT; here they are raised so the 8 concurrent activations all
        // reach the PostgreSQL row lock (with the default 5 per token, the extra ones get 429 before the lock).
        activacionEncendida(r, 1000, 1000);
    }

    @Override GenericContainer<?> mailpit() { return correo; }

    @BeforeEach void preparar() throws Exception { prepararAdministrador(); }

    private static String nuevoCorreo() { return "d05." + UUID.randomUUID() + "@prisaly.test"; }

    private int vivos(UUID docente) {
        return jdbc.queryForObject("SELECT count(*) FROM \"ActivacionDocente\" WHERE \"idUsuario\"=?"
                + " AND \"consumidoEn\" IS NULL AND \"revocadoEn\" IS NULL", Integer.class, docente);
    }

    private String passwordHash(UUID docente) {
        return jdbc.queryForObject("SELECT \"passwordHash\" FROM \"Usuario\" WHERE \"idUsuario\"=?", String.class, docente);
    }

    @Test
    void altaEnviaInvitacionYElDocenteDefineSuContrasenaUnaSolaVez() throws Exception {
        String correoDocente = nuevoCorreo();
        UUID docente = alta(correoDocente);
        String token = tokenDelCorreo(correoDocente, 1);

        // Only the SHA-256 is stored, with the configured validity (PT1H).
        assertEquals(1, vivos(docente));
        assertEquals(ActivacionDocenteService.hash(token), jdbc.queryForObject(
                "SELECT \"hashToken\" FROM \"ActivacionDocente\" WHERE \"idUsuario\"=?", String.class, docente));
        assertEquals(0, jdbc.queryForObject("SELECT count(*) FROM \"ActivacionDocente\" WHERE \"hashToken\"=?",
                Integer.class, token), "the clear token is never persisted");
        assertEquals(3600L, jdbc.queryForObject("SELECT extract(epoch FROM \"expiraEn\" - \"emitidoEn\")::bigint"
                + " FROM \"ActivacionDocente\" WHERE \"idUsuario\"=?", Long.class, docente));
        assertEquals(admin, jdbc.queryForObject("SELECT \"idEmisor\" FROM \"ActivacionDocente\" WHERE \"idUsuario\"=?",
                UUID.class, docente));
        assertNull(passwordHash(docente));

        // DM1-08 rejects without consuming the token.
        error(activar(token, "corta"), 422, "PASSWORD_TOO_SHORT");
        error(activar(token, correoDocente + "#Extra-2026"), 422, "PASSWORD_CONTAINS_EMAIL");
        assertEquals(1, vivos(docente));

        HttpResponse<String> ok = activar(token, CLAVE);
        assertEquals(204, ok.statusCode(), ok.body());
        assertTrue(ok.headers().firstValue("Cache-Control").orElse("").contains("no-store"));
        String hash = passwordHash(docente);
        assertNotNull(hash);
        assertTrue(BCrypt.checkpw(CLAVE, hash), "stored with the existing BCrypt encoder");
        assertEquals(0, vivos(docente));

        // The teacher can now log in; the token cannot be reused; issuing again is not allowed.
        String tokenDocente = login(correoDocente, CLAVE);
        var me = send("GET", "/v1/personal/me", tokenDocente, null);
        assertEquals(200, me.statusCode(), me.body());
        assertTrue(me.body().contains(ROL_DOCENTE), me.body());
        error(activar(token, "Otra#Clave-Distinta-2026"), 400, "ACTIVATION_INVALID");
        assertEquals(hash, passwordHash(docente), "reuse never changes the credential");
        error(send("POST", "/v1/admin/docentes/" + docente + "/activacion", tokenAdmin, null), 409,
                "ACTIVATION_NOT_ALLOWED");
    }

    @Test
    void reemisionYRevocacionExplicitaInvalidanLosTokensAnteriores() throws Exception {
        String correoDocente = nuevoCorreo();
        UUID docente = alta(correoDocente);
        String t1 = tokenDelCorreo(correoDocente, 1);
        var reemision = send("POST", "/v1/admin/docentes/" + docente + "/activacion", tokenAdmin, null);
        assertEquals(202, reemision.statusCode(), reemision.body());
        assertTrue(reemision.body().isEmpty(), "the token is never returned to the administrator");
        String t2 = tokenDelCorreo(correoDocente, 2);
        assertNotEquals(t1, t2);
        assertEquals(1, vivos(docente));
        error(activar(t1, CLAVE), 400, "ACTIVATION_INVALID");

        assertEquals(204, send("DELETE", "/v1/admin/docentes/" + docente + "/activacion", tokenAdmin, null).statusCode());
        assertEquals(0, vivos(docente));
        error(activar(t2, CLAVE), 400, "ACTIVATION_INVALID");
        assertEquals(204, send("DELETE", "/v1/admin/docentes/" + docente + "/activacion", tokenAdmin, null).statusCode(),
                "idempotent");

        assertEquals(202, send("POST", "/v1/admin/docentes/" + docente + "/activacion", tokenAdmin, null).statusCode());
        String t3 = tokenDelCorreo(correoDocente, 3);
        assertEquals(204, activar(t3, CLAVE).statusCode());
        assertEquals(3, jdbc.queryForObject("SELECT count(*) FROM \"ActivacionDocente\" WHERE \"idUsuario\"=?",
                Integer.class, docente), "full history kept: 2 revoked + 1 consumed");
    }

    @Test
    void soloElAdministradorEmiteYRevoca() throws Exception {
        String correoDocente = nuevoCorreo();
        UUID docente = alta(correoDocente);
        assertEquals(204, activar(tokenDelCorreo(correoDocente, 1), CLAVE).statusCode());
        String tokenDocente = login(correoDocente, CLAVE);
        UUID otro = alta(nuevoCorreo());
        String ruta = "/v1/admin/docentes/" + otro + "/activacion";

        error(send("POST", ruta, null, null), 401, "UNAUTHENTICATED");
        error(send("DELETE", ruta, null, null), 401, "UNAUTHENTICATED");
        error(send("POST", ruta, "token-falsificado", null), 401, "UNAUTHENTICATED");
        error(send("POST", ruta, tokenDocente, null), 403, "ADMIN_REQUIRED");
        error(send("DELETE", ruta, tokenDocente, null), 403, "ADMIN_REQUIRED");
        error(send("POST", "/v1/admin/docentes/" + UUID.randomUUID() + "/activacion", tokenAdmin, null), 404,
                "USER_NOT_FOUND");
        error(send("POST", "/v1/admin/docentes/no-es-uuid/activacion", tokenAdmin, null), 400, "VALIDATION_ERROR");
        error(send("POST", "/v1/admin/docentes/" + admin + "/activacion", tokenAdmin, null), 409,
                "ACTIVATION_NOT_ALLOWED");
        assertEquals(1, vivos(otro), "rejected callers changed nothing");
        assertNotNull(passwordHash(docente));
    }

    @Test
    void todosLosRechazosDeTokenSonIgualesEIncluyenElCaducado() throws Exception {
        String correoDocente = nuevoCorreo();
        UUID docente = alta(correoDocente);
        tokenDelCorreo(correoDocente, 1);
        assertEquals(204, send("DELETE", "/v1/admin/docentes/" + docente + "/activacion", tokenAdmin, null).statusCode());
        // An expired row for a known code (written directly: the service never issues one in the past).
        String caducado = "C".repeat(43);
        Instant ahora = Instant.now().truncatedTo(ChronoUnit.SECONDS);
        jdbc.update("INSERT INTO \"ActivacionDocente\" (\"hashToken\",\"idUsuario\",\"idEmisor\",\"emitidoEn\",\"expiraEn\")"
                + " VALUES (?,?,?,?,?)", ActivacionDocenteService.hash(caducado), docente, admin,
                Timestamp.from(ahora.minusSeconds(7200)), Timestamp.from(ahora.minusSeconds(3600)));

        List<HttpResponse<String>> rechazos = List.of(activar(caducado, CLAVE), activar("D".repeat(43), CLAVE),
                activar("corto", CLAVE), activar("E".repeat(42) + "!", CLAVE));
        for (var r : rechazos) { error(r, 400, "ACTIVATION_INVALID"); }
        assertEquals(1, rechazos.stream().map(HttpResponse::body).distinct().count(), "identical bodies");
        error(send("POST", "/v1/personal/activar", null, "{\"token\":\"x\"}"), 400, "VALIDATION_ERROR");
        error(send("POST", "/v1/personal/activar", null, "{no json"), 400, "VALIDATION_ERROR");
        assertNull(passwordHash(docente));
    }

    @Test
    void ochoActivacionesConcurrentesDelMismoTokenSoloUnaGana() throws Exception {
        String correoDocente = nuevoCorreo();
        UUID docente = alta(correoDocente);
        String token = tokenDelCorreo(correoDocente, 1);
        CountDownLatch salida = new CountDownLatch(1);
        ExecutorService hilos = Executors.newFixedThreadPool(8);
        List<Future<Integer>> resultados = new ArrayList<>();
        for (int i = 0; i < 8; i++) {
            String clave = "Concurrente#" + i + "-Clave-2026";
            Callable<Integer> intento = () -> { salida.await(); return activar(token, clave).statusCode(); };
            resultados.add(hilos.submit(intento));
        }
        salida.countDown();
        List<Integer> codigos = new ArrayList<>();
        for (var f : resultados) { codigos.add(f.get()); }
        hilos.shutdown();
        assertEquals(1, codigos.stream().filter(c -> c == 204).count(), codigos.toString());
        assertEquals(7, codigos.stream().filter(c -> c == 400).count(), codigos.toString());
        String hash = passwordHash(docente);
        long coincidencias = java.util.stream.IntStream.range(0, 8)
                .filter(i -> BCrypt.checkpw("Concurrente#" + i + "-Clave-2026", hash)).count();
        assertEquals(1, coincidencias, "exactly one password was stored");
    }

    @Test
    void elEsquemaCandidatoProtegeElHistorial() throws Exception {
        String correoDocente = nuevoCorreo();
        UUID docente = alta(correoDocente);
        String token = tokenDelCorreo(correoDocente, 1);
        String hash = ActivacionDocenteService.hash(token);
        Instant ahora = Instant.now();
        // At most one live token per teacher.
        assertThrows(DataAccessException.class, () -> jdbc.update("INSERT INTO \"ActivacionDocente\" (\"hashToken\","
                + "\"idUsuario\",\"idEmisor\",\"emitidoEn\",\"expiraEn\") VALUES (?,?,?,?,?)",
                ActivacionDocenteService.hash("F".repeat(43)), docente, admin, Timestamp.from(ahora),
                Timestamp.from(ahora.plusSeconds(60))));
        // Only lowercase hex hashes; expiry after issue.
        assertThrows(DataAccessException.class, () -> jdbc.update("UPDATE \"ActivacionDocente\" SET \"expiraEn\"="
                + "\"emitidoEn\" WHERE \"hashToken\"=?", hash));
        assertEquals(204, activar(token, CLAVE).statusCode());
        // A closed row is immutable and nothing is ever deleted.
        assertThrows(DataAccessException.class, () -> jdbc.update("UPDATE \"ActivacionDocente\" SET \"consumidoEn\"=NULL"
                + " WHERE \"hashToken\"=?", hash));
        assertThrows(DataAccessException.class, () -> jdbc.update("DELETE FROM \"ActivacionDocente\" WHERE \"hashToken\"=?",
                hash));
        assertEquals(1, jdbc.queryForObject("SELECT count(*) FROM \"ActivacionDocente\" WHERE \"hashToken\"=?"
                + " AND \"consumidoEn\" IS NOT NULL", Integer.class, hash));
    }
}
