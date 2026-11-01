package com.prisaly.m7;

import static org.junit.jupiter.api.Assertions.*;
import com.prisaly.m4.application.port.*;
import com.prisaly.m4.application.service.SesionAcademicaService;
import com.prisaly.m4.domain.rule.ReglaNegocioException;
import com.prisaly.m4.infrastructure.adapter.*;
import com.prisaly.m4.infrastructure.persistence.*;
import com.prisaly.m7.application.*;
import com.prisaly.m7.domain.*;
import com.prisaly.m7.infrastructure.*;
import java.nio.file.Path;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.persistence.autoconfigure.EntityScan;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.postgresql.PostgreSQLContainer;

/** M4 y M7 reales. Identidad docente de prueba; no certifica autenticación productiva M1. */
@SpringBootTest(classes = M7ConvocatoriaPostgreSqlIT.TestApplication.class)
class M7ConvocatoriaPostgreSqlIT {
    static final PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:16.15");
    static { postgres.start(); }

    @DynamicPropertySource static void propiedades(DynamicPropertyRegistry r) {
        r.add("spring.datasource.url", postgres::getJdbcUrl);
        r.add("spring.datasource.username", postgres::getUsername);
        r.add("spring.datasource.password", postgres::getPassword);
        r.add("spring.jpa.hibernate.ddl-auto", () -> "validate");
        r.add("spring.jpa.properties.hibernate.globally_quoted_identifiers", () -> "true");
        r.add("spring.flyway.locations", () -> "filesystem:" + Path.of(
                "../app/src/main/resources/db/migration").toAbsolutePath());
    }

    @Autowired SesionAcademicaService service;
    @Autowired NotificacionPort notificaciones;
    @Autowired RegistroAvisoPort registro;
    @Autowired JdbcTemplate jdbc;
    @Autowired PlatformTransactionManager txManager;
    @Autowired AtomicBoolean falloPosterior;
    UUID docente, estudiante, usuario, caso, sesion;

    @BeforeEach void preparar() {
        falloPosterior.set(false);
        jdbc.execute("TRUNCATE TABLE \"Rol\" RESTART IDENTITY CASCADE");
        docente = UUID.randomUUID(); usuario = UUID.randomUUID(); estudiante = UUID.randomUUID();
        caso = UUID.randomUUID();
        jdbc.update("INSERT INTO \"Rol\" (\"idRol\",\"nombre\",\"descripcion\") VALUES (1,'Fixture','Test')");
        usuario(docente); usuario(usuario);
        jdbc.update("INSERT INTO \"Estudiante\" (\"idEstudiante\",\"idUsuario\") VALUES (?,?)", estudiante, usuario);
        jdbc.update("""
                INSERT INTO "Caso" ("idCaso","idDocente","nombre","tiempoEstimadoSegundos","estado")
                VALUES (?,?,'Caso test',60,'Publicado')
                """, caso, docente);
        Instant inicio = Instant.now().plusSeconds(86400);
        sesion = service.crearSesion(docente, caso, "Espacio test", null, inicio, inicio.plusSeconds(3600), 600).getId();
    }

    void usuario(UUID id) {
        jdbc.update("""
                INSERT INTO "Usuario" ("idUsuario","idRol","nombre","correo") VALUES (?,1,'Fixture',?)
                """, id, id + "@example.invalid");
    }

    int cantidad(String tabla) {
        // Solo constantes locales del test.
        return jdbc.queryForObject("SELECT count(*) FROM \"" + tabla + "\"", Integer.class);
    }

    TransactionTemplate tx() { return new TransactionTemplate(txManager); }

    @Test void convocatoriaYAvisoAtomicosConDestinatarioCorrecto() {
        var convocatoria = service.convocarEstudiante(docente, sesion, estudiante);
        assertEquals(1, cantidad("Convocatoria"));
        assertEquals(1, cantidad("Aviso"));
        var aviso = jdbc.queryForMap("SELECT * FROM \"Aviso\"");
        assertEquals(usuario, aviso.get("idUsuarioDestino"));
        assertNotEquals(estudiante, aviso.get("idUsuarioDestino"));
        assertEquals("convocatoria:" + convocatoria.getId(), aviso.get("claveEvento"));
        assertEquals("Convocatoria", aviso.get("tipo"));
        assertNull(aviso.get("idParticipacion"));
        assertNull(aviso.get("fechaLectura"));
        assertEquals(0, cantidad("EnvioCorreo"));
        assertNotNull(jdbc.queryForObject("SELECT \"fechaBloqueoAcademico\" FROM \"Caso\" WHERE \"idCaso\"=?",
                Object.class, caso));
        assertEquals(9, jdbc.queryForObject("SELECT count(*) FROM flyway_schema_history WHERE success", Integer.class));
    }

    @Test void rechazoDeM4NoGeneraAviso() {
        assertThrows(ReglaNegocioException.class, () -> service.convocarEstudiante(null, sesion, estudiante));
        assertEquals(0, cantidad("Convocatoria")); assertEquals(0, cantidad("Aviso"));
    }

    @Test void destinatarioInexistenteRevierteConvocatoria() {
        assertThrows(RuntimeException.class, () -> service.convocarEstudiante(docente, sesion, UUID.randomUUID()));
        assertEquals(0, cantidad("Convocatoria")); assertEquals(0, cantidad("Aviso"));
    }

    @Test void observadorPosteriorFallaYRevierteAmbasEscrituras() {
        falloPosterior.set(true);
        var error = assertThrows(ReglaNegocioException.class,
                () -> service.convocarEstudiante(docente, sesion, estudiante));
        assertEquals("M4-PROVEEDOR-NO-DISPONIBLE", error.getCodigo());
        assertEquals(0, cantidad("Convocatoria")); assertEquals(0, cantidad("Aviso"));
    }

    @Test void falloDespuesDelServicioAntesDeCommitRevierteAmbos() {
        assertThrows(IllegalStateException.class, () -> tx().executeWithoutResult(s -> {
            service.convocarEstudiante(docente, sesion, estudiante);
            assertEquals(1, cantidad("Aviso"));
            throw new IllegalStateException("Fallo exterior antes del commit");
        }));
        assertEquals(0, cantidad("Convocatoria")); assertEquals(0, cantidad("Aviso"));
    }

    @Test void repeticionNoDuplicaNiReescribeLectura() {
        service.convocarEstudiante(docente, sesion, estudiante);
        jdbc.update("UPDATE \"Aviso\" SET \"fechaLectura\"=now()");
        var previo = jdbc.queryForMap("SELECT * FROM \"Aviso\"");
        tx().executeWithoutResult(s -> notificaciones.notificarConvocatoria(sesion, estudiante));
        assertEquals(previo, jdbc.queryForMap("SELECT * FROM \"Aviso\""));
        assertThrows(ReglaNegocioException.class, () -> service.convocarEstudiante(docente, sesion, estudiante));
        assertEquals(1, cantidad("Aviso"));
    }

    @Test void sinConvocatoriaNoSeRegistra() {
        assertThrows(ReglaNegocioException.class,
                () -> tx().executeWithoutResult(s -> notificaciones.notificarConvocatoria(sesion, estudiante)));
        assertEquals(0, cantidad("Aviso"));
    }

    @Test void falloSqlDeAvisoRevierteConvocatoria() {
        // Regla adicional SOLO en contenedor efímero para provocar error real de persistencia.
        jdbc.execute("ALTER TABLE \"Aviso\" ADD CONSTRAINT prueba_fallo CHECK (false)");
        try {
            assertThrows(ReglaNegocioException.class, () -> service.convocarEstudiante(docente, sesion, estudiante));
            assertEquals(0, cantidad("Convocatoria")); assertEquals(0, cantidad("Aviso"));
        } finally { jdbc.execute("ALTER TABLE \"Aviso\" DROP CONSTRAINT prueba_fallo"); }
    }

    @Test void integridadReferencialDeAviso() {
        var evento = new ConvocatoriaNotificable(UUID.randomUUID(), sesion, estudiante, UUID.randomUUID());
        assertThrows(RuntimeException.class,
                () -> tx().executeWithoutResult(s -> registro.registrar(new AvisoConvocatoria(evento, "Aviso"))));
        assertEquals(0, cantidad("Aviso"));
    }

    @Test void dosDestinatariosMismaSesionSonEventosDistintos() {
        UUID usuario2 = UUID.randomUUID(), estudiante2 = UUID.randomUUID();
        usuario(usuario2);
        jdbc.update("INSERT INTO \"Estudiante\" (\"idEstudiante\",\"idUsuario\") VALUES (?,?)", estudiante2, usuario2);
        service.convocarEstudiante(docente, sesion, estudiante);
        service.convocarEstudiante(docente, sesion, estudiante2);
        assertEquals(2, cantidad("Aviso"));
        assertEquals(2, jdbc.queryForObject("SELECT count(DISTINCT \"claveEvento\") FROM \"Aviso\"", Integer.class));
    }

    @Test void dosRegistrosConcurrentesDelMismoEventoNoDuplican() throws Exception {
        service.convocarEstudiante(docente, sesion, estudiante);
        jdbc.update("DELETE FROM \"Aviso\"");
        var inicio = new CountDownLatch(1);
        try (var pool = Executors.newFixedThreadPool(2)) {
            Callable<Void> tarea = () -> {
                assertTrue(inicio.await(10, TimeUnit.SECONDS));
                tx().executeWithoutResult(s -> notificaciones.notificarConvocatoria(sesion, estudiante));
                return null;
            };
            var a = pool.submit(tarea); var b = pool.submit(tarea); inicio.countDown();
            a.get(30, TimeUnit.SECONDS); b.get(30, TimeUnit.SECONDS);
        }
        assertEquals(1, cantidad("Aviso"));
    }

    @Test void dosConvocatoriasConcurrentesSoloUnaEsExitosa() throws Exception {
        var inicio = new CountDownLatch(1);
        try (var pool = Executors.newFixedThreadPool(2)) {
            Callable<Boolean> tarea = () -> {
                assertTrue(inicio.await(10, TimeUnit.SECONDS));
                try { service.convocarEstudiante(docente, sesion, estudiante); return true; }
                catch (ReglaNegocioException e) {
                    assertEquals("M4-CONVOCATORIA-DUPLICADA", e.getCodigo()); return false;
                }
            };
            var a = pool.submit(tarea); var b = pool.submit(tarea); inicio.countDown();
            assertNotEquals(a.get(30, TimeUnit.SECONDS), b.get(30, TimeUnit.SECONDS));
        }
        assertEquals(1, cantidad("Convocatoria")); assertEquals(1, cantidad("Aviso"));
    }

    @SpringBootConfiguration
    @EnableAutoConfiguration
    @EntityScan(basePackageClasses = {ConvocatoriaEntity.class, AvisoEntity.class})
    @EnableJpaRepositories(basePackageClasses = JpaConvocatoriaRepository.class)
    @Import({M7Configuration.class, SesionAcademicaService.class,
            SesionRepositoryAdapter.class, ConvocatoriaRepositoryAdapter.class})
    static class TestApplication {
        @Bean IdentidadPort identidadDePrueba() { return (id, permiso) -> true; }
        @Bean AtomicBoolean falloPosterior() { return new AtomicBoolean(); }
        @Bean ObservadorConvocatoria observadorDeFalloSoloTest(AtomicBoolean fallo) {
            return evento -> { if (fallo.get()) throw new IllegalStateException("Fallo de test"); };
        }
    }
}
