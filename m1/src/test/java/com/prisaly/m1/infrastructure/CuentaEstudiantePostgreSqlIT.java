package com.prisaly.m1.infrastructure;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.prisaly.m1.application.service.IdentityAccessService;
import com.prisaly.m1.domain.student.FirmasEstudiante;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.springframework.dao.DataIntegrityViolationException;

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
import org.springframework.context.annotation.Bean;
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

    private static final ClaveHmacEstudiante CLAVE =
            new ClaveHmacEstudiante("clave-hmac-prueba".getBytes(StandardCharsets.UTF_8));

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

    @Autowired
    private ActivacionCuentaEstudianteService activacionTransaccional;

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

    // Uses the Spring-managed (transactional) service: a denied activation must not roll back
    // the failed-attempt counter, otherwise the 5-attempt limit is never reached.
    @Test
    void losIntentosFallidosPersistenYBloqueanElCodigoCorrecto() {
        UUID usuario = insertar("bruta.pg@prisaly.test");
        var emitido = activacionTransaccional.emitir(usuario);
        String erroneo = emitido.codigo().equals("00000000") ? "11111111" : "00000000";

        for (int intento = 0; intento < ActivacionCuentaEstudianteService.INTENTOS_MAXIMOS; intento++) {
            assertThrows(IdentityDomainException.class,
                    () -> activacionTransaccional.activar(emitido.challengeId(), erroneo, "contrasena-segura"));
        }

        assertEquals(
                ActivacionCuentaEstudianteService.INTENTOS_MAXIMOS,
                jdbc.queryForObject(
                        "SELECT \"intentosFallidos\" FROM \"ActivacionCuentaEstudiante\" WHERE \"challengeId\" = ?",
                        Integer.class,
                        emitido.challengeId()));
        assertThrows(IdentityDomainException.class,
                () -> activacionTransaccional.activar(
                        emitido.challengeId(), emitido.codigo(), "contrasena-segura"));
        assertEquals("Inactivo", jdbc.queryForObject(
                "SELECT \"estado\" FROM \"Usuario\" WHERE \"idUsuario\" = ?", String.class, usuario));
    }

    @Test
    void laActivacionVencidaOReutilizadaNoActivaLaCuenta() {
        UUID vencida = insertar("vencida.pg@prisaly.test");
        Instant base = Instant.now();
        var emisor = new ActivacionCuentaEstudianteService(cuentas, encoder, CLAVE, Clock.fixed(base, ZoneOffset.UTC));
        var tarde = new ActivacionCuentaEstudianteService(cuentas, encoder, CLAVE,
                Clock.fixed(base.plus(ActivacionCuentaEstudianteService.VIGENCIA).plusSeconds(1), ZoneOffset.UTC));
        var emitido = emisor.emitir(vencida);
        assertThrows(IdentityDomainException.class,
                () -> tarde.activar(emitido.challengeId(), emitido.codigo(), "contrasena-segura"));
        assertEquals("Inactivo", estado(vencida));

        UUID reutilizada = insertar("reuso.pg@prisaly.test");
        var codigo = activacionTransaccional.emitir(reutilizada);
        activacionTransaccional.activar(codigo.challengeId(), codigo.codigo(), "contrasena-segura");
        String hash = passwordHash(reutilizada);
        assertThrows(IdentityDomainException.class,
                () -> activacionTransaccional.activar(codigo.challengeId(), codigo.codigo(), "otra-contrasena-larga"));
        assertEquals(hash, passwordHash(reutilizada), "a consumed code must not reset the password");
    }

    @Test
    void dosActivacionesConcurrentesDelMismoCodigoTienenUnSoloGanador() throws Exception {
        UUID usuario = insertar("concurrente.pg@prisaly.test");
        var emitido = activacionTransaccional.emitir(usuario);
        CountDownLatch salida = new CountDownLatch(1);
        ExecutorService hilos = Executors.newFixedThreadPool(2);
        try {
            List<Future<Boolean>> resultados = new ArrayList<>();
            for (String contrasena : List.of("contrasena-hilo-uno", "contrasena-hilo-dos")) {
                Callable<Boolean> intento = () -> {
                    salida.await();
                    try {
                        activacionTransaccional.activar(emitido.challengeId(), emitido.codigo(), contrasena);
                        return true;
                    } catch (IdentityDomainException | IllegalStateException denegado) {
                        return false;
                    }
                };
                resultados.add(hilos.submit(intento));
            }
            salida.countDown();
            int ganadores = 0;
            for (Future<Boolean> resultado : resultados) {
                ganadores += resultado.get(30, TimeUnit.SECONDS) ? 1 : 0;
            }
            assertEquals(1, ganadores);
        } finally {
            hilos.shutdownNow();
        }
        assertEquals("Activo", estado(usuario));
        assertEquals(1, jdbc.queryForObject(
                "SELECT count(*) FROM \"ActivacionCuentaEstudiante\" WHERE \"idUsuario\" = ? AND \"fechaConsumo\" IS NOT NULL",
                Integer.class, usuario));
    }

    @Test
    void bd0052ImpideUnaSegundaVinculacionDelMismoUsuario() {
        UUID usuario = insertar("duplicado.pg@prisaly.test");
        assertThrows(DataIntegrityViolationException.class, () -> jdbc.update(
                "INSERT INTO \"Estudiante\" (\"idEstudiante\", \"idUsuario\") VALUES (?, ?)", UUID.randomUUID(), usuario));
    }

    // V011 alone has two independent FKs and accepted a student of another user (reproduced in
    // DC-M1-CIERRE-02). The V012 candidate rejects cross links for activations and sessions.
    @Test
    void v012RechazaVinculosCruzadosEnActivacionesYSesiones() {
        UUID victima = insertar("victima.pg@prisaly.test");
        UUID atacante = insertar("atacante.pg@prisaly.test");
        UUID estudianteVictima = estudiante(victima);

        DataIntegrityViolationException activacion = assertThrows(DataIntegrityViolationException.class, () -> jdbc.update("""
                INSERT INTO "ActivacionCuentaEstudiante" ("idActivacion", "idUsuario", "idEstudiante", "challengeId",
                  "codigoHmac", "fechaExpiracion")
                VALUES (?, ?, ?, ?, ?, now() + interval '10 minutes')
                """, UUID.randomUUID(), atacante, estudianteVictima, UUID.randomUUID(), new byte[] {1}));
        assertTrue(activacion.getMessage().contains("DD02-VINCULO-INCOHERENTE"));

        jdbc.update("UPDATE \"Usuario\" SET \"estado\" = 'Activo' WHERE \"idUsuario\" = ?", atacante);
        DataIntegrityViolationException sesion = assertThrows(DataIntegrityViolationException.class, () -> insertarSesion(
                atacante, estudianteVictima));
        assertTrue(sesion.getMessage().contains("DD02-VINCULO-INCOHERENTE"));
    }

    @Test
    void v012NoAbreSesionesDeCuentasInactivasNiCambiaElVinculoEnUso() {
        UUID usuario = insertar("vinculo.pg@prisaly.test");
        UUID estudiante = estudiante(usuario);
        DataIntegrityViolationException inactiva = assertThrows(DataIntegrityViolationException.class,
                () -> insertarSesion(usuario, estudiante));
        assertTrue(inactiva.getMessage().contains("DD02-CUENTA-INACTIVA"));

        activacionTransaccional.emitir(usuario);
        UUID otro = insertarUsuarioSinEstudiante("otro.pg@prisaly.test");
        DataIntegrityViolationException enUso = assertThrows(DataIntegrityViolationException.class, () -> jdbc.update(
                "UPDATE \"Estudiante\" SET \"idUsuario\" = ? WHERE \"idEstudiante\" = ?", otro, estudiante));
        assertTrue(enUso.getMessage().contains("DD02-VINCULO-EN-USO"));
    }

    @Test
    void v012DejaUnSoloCodigoAbiertoAunqueSeEmitaEnParalelo() throws Exception {
        UUID usuario = insertar("emision.pg@prisaly.test");
        CountDownLatch salida = new CountDownLatch(1);
        ExecutorService hilos = Executors.newFixedThreadPool(2);
        try {
            List<Future<Boolean>> resultados = new ArrayList<>();
            for (int i = 0; i < 2; i++) {
                resultados.add(hilos.submit(() -> {
                    salida.await();
                    try {
                        activacionTransaccional.emitir(usuario);
                        return true;
                    } catch (DataIntegrityViolationException carrera) {
                        return false;
                    }
                }));
            }
            salida.countDown();
            for (Future<Boolean> resultado : resultados) {
                resultado.get(30, TimeUnit.SECONDS);
            }
        } finally {
            hilos.shutdownNow();
        }
        assertEquals(1, jdbc.queryForObject("""
                SELECT count(*) FROM "ActivacionCuentaEstudiante"
                WHERE "idUsuario" = ? AND "fechaConsumo" IS NULL AND "fechaRevocacion" IS NULL
                """, Integer.class, usuario));
        assertThrows(DataIntegrityViolationException.class, () -> jdbc.update("""
                UPDATE "ActivacionCuentaEstudiante" SET "fechaConsumo" = now(), "fechaRevocacion" = now()
                WHERE "idUsuario" = ?
                """, usuario));
    }

    @Test
    void laSesionCaducaPorInactividadYElUsoLaMantieneViva() {
        UUID usuario = insertar("inactividad.pg@prisaly.test");
        var emitido = activacionTransaccional.emitir(usuario);
        activacionTransaccional.activar(emitido.challengeId(), emitido.codigo(), "contrasena-segura");
        Instant base = Instant.now();
        String token = new AutenticacionEstudianteService(cuentas, encoder, CLAVE, Clock.fixed(base, ZoneOffset.UTC))
                .autenticar("inactividad.pg@prisaly.test", "contrasena-segura").token();

        assertEquals(usuario, accesoEn(token, base.plus(Duration.ofMinutes(20))).requireCurrentStudent().userId());
        // 25 min after the last use: still inside the 30 min window.
        assertEquals(usuario, accesoEn(token, base.plus(Duration.ofMinutes(45))).requireCurrentStudent().userId());
        // 35 min idle: rejected, and the rejection does not refresh the session.
        assertThrows(IdentityDomainException.class, () -> accesoEn(token, base.plus(Duration.ofMinutes(80))).requireCurrentStudent());
        assertThrows(IdentityDomainException.class, () -> accesoEn(token, base.plus(Duration.ofMinutes(81))).requireCurrentStudent());
    }

    private SessionStudentAccessService accesoEn(String token, Instant instante) {
        return new SessionStudentAccessService(cuentas, () -> Optional.of(token), CLAVE, Clock.fixed(instante, ZoneOffset.UTC));
    }

    private void insertarSesion(UUID usuario, UUID estudiante) {
        jdbc.update("""
                INSERT INTO "SesionEstudiante" ("idSesionEstudiante", "idUsuario", "idEstudiante", "sesionTokenHmac",
                  "fechaExpiracion", "fechaUltimaActividad")
                VALUES (?, ?, ?, ?, now() + interval '1 hour', now())
                """, UUID.randomUUID(), usuario, estudiante, UUID.randomUUID().toString().getBytes(StandardCharsets.UTF_8));
    }

    private UUID estudiante(UUID usuario) {
        return jdbc.queryForObject("SELECT \"idEstudiante\" FROM \"Estudiante\" WHERE \"idUsuario\" = ?", UUID.class, usuario);
    }

    @Test
    void desactivarLaCuentaInvalidaSuSesionYUnTokenDesconocidoNoEsIdentidad() {
        UUID usuario = insertar("desactivada.pg@prisaly.test");
        var autenticacion = new AutenticacionEstudianteService(cuentas, encoder, CLAVE);
        AtomicReference<String> token = new AtomicReference<>();
        var acceso = new SessionStudentAccessService(cuentas, () -> Optional.ofNullable(token.get()), CLAVE);
        var emitido = activacionTransaccional.emitir(usuario);
        activacionTransaccional.activar(emitido.challengeId(), emitido.codigo(), "contrasena-segura");
        token.set(autenticacion.autenticar("desactivada.pg@prisaly.test", "contrasena-segura").token());
        assertEquals(usuario, acceso.requireCurrentStudent().userId());

        jdbc.update("UPDATE \"Usuario\" SET \"estado\" = 'Inactivo' WHERE \"idUsuario\" = ?", usuario);
        IdentityDomainException inactiva = assertThrows(IdentityDomainException.class, acceso::requireCurrentStudent);
        assertEquals(IdentityAccessService.CODE_USER_INACTIVE, inactiva.code());
        assertThrows(IdentityDomainException.class,
                () -> autenticacion.autenticar("desactivada.pg@prisaly.test", "contrasena-segura"));

        token.set("token-que-el-servidor-nunca-emitio");
        assertThrows(IdentityDomainException.class, acceso::requireCurrentStudent);
        token.set(null);
        assertThrows(IdentityDomainException.class, acceso::requireCurrentStudent);
    }

    private String estado(UUID usuario) {
        return jdbc.queryForObject("SELECT \"estado\" FROM \"Usuario\" WHERE \"idUsuario\" = ?", String.class, usuario);
    }

    private String passwordHash(UUID usuario) {
        String hash = jdbc.queryForObject(
                "SELECT \"passwordHash\" FROM \"Usuario\" WHERE \"idUsuario\" = ?", String.class, usuario);
        assertTrue(hash != null && !hash.isBlank());
        return hash;
    }

    private UUID insertar(String correo) {
        UUID usuario = insertarUsuarioSinEstudiante(correo);
        jdbc.update(
                "INSERT INTO \"Estudiante\" (\"idEstudiante\", \"idUsuario\") VALUES (?, ?)",
                UUID.randomUUID(), usuario);
        return usuario;
    }

    private UUID insertarUsuarioSinEstudiante(String correo) {
        Integer rol = jdbc.queryForObject(
                "INSERT INTO \"Rol\" (\"nombre\", \"descripcion\") VALUES (?, ?) RETURNING \"idRol\"",
                Integer.class,
                // Rol.nombre is varchar(30) in BD-005.2.
                "Rol-" + UUID.randomUUID().toString().substring(0, 8),
                "Prueba");
        UUID usuario = UUID.randomUUID();
        jdbc.update(
                """
                INSERT INTO "Usuario" ("idUsuario", "idRol", "nombre", "correo", "estado")
                VALUES (?, ?, ?, ?, 'Inactivo')
                """,
                usuario, rol, correo, correo);
        return usuario;
    }

    // V011/V012 live outside the productive Flyway location until DD-02 is ratified;
    // this IT applies BD-005.2 plus the pending DD-02 candidates explicitly.
    static String flyway() {
        Path cwd = Path.of(System.getProperty("user.dir")).toAbsolutePath().normalize();
        for (Path resources : List.of(
                cwd.resolve("app/src/main/resources/db"),
                cwd.resolve("../app/src/main/resources/db").normalize())) {
            Path canonica = resources.resolve("migration");
            Path pendiente = resources.resolve("dd02-propuesta");
            if (Files.exists(pendiente.resolve("V011__acceso_estudiante_cuenta.sql"))
                    && Files.exists(pendiente.resolve("V012__dd02_integridad_vinculo.sql"))
                    && Files.exists(canonica.resolve("V001__bd0052_estructura.sql"))) {
                return "filesystem:" + canonica + ",filesystem:" + pendiente;
            }
        }
        throw new IllegalStateException("No se encontró V011 en db/dd02-propuesta desde " + cwd);
    }

    @SpringBootConfiguration
    @EnableAutoConfiguration
    @Import({CuentaEstudianteJdbcAdapter.class, PasswordHashConfig.class})
    static class App {

        @Bean
        ClaveHmacEstudiante claveHmacEstudiante() {
            return CLAVE;
        }

        @Bean
        ActivacionCuentaEstudianteService activacionCuentaEstudianteService(
                CuentaEstudiantePort cuentas, PasswordEncoder encoder, ClaveHmacEstudiante clave) {
            return new ActivacionCuentaEstudianteService(cuentas, encoder, clave);
        }
    }
}
