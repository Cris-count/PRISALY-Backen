package com.prisaly.m7;

import static org.junit.jupiter.api.Assertions.*;

import com.prisaly.m4.application.port.IdentidadPort;
import com.prisaly.m4.application.port.NotificacionPort;
import com.prisaly.m4.application.service.SesionAcademicaService;
import com.prisaly.m4.domain.rule.ReglaNegocioException;
import com.prisaly.m4.infrastructure.adapter.ConvocatoriaRepositoryAdapter;
import com.prisaly.m4.infrastructure.adapter.SesionRepositoryAdapter;
import com.prisaly.m4.infrastructure.persistence.ConvocatoriaEntity;
import com.prisaly.m4.infrastructure.persistence.JpaConvocatoriaRepository;
import com.prisaly.m7.application.NotificarConvocatoriaCommand;
import com.prisaly.m7.application.NotificarConvocatoriaService;
import com.prisaly.m7.application.RegistroAvisoPort;
import com.prisaly.m7.application.RegistroEnvioCorreoPort;
import com.prisaly.m7.domain.AvisoConvocatoria;
import com.prisaly.m7.domain.ConvocatoriaNotificable;
import com.prisaly.m7.domain.EnvioCorreoConvocatoria;
import com.prisaly.m7.infrastructure.AvisoEntity;
import com.prisaly.m7.infrastructure.EnvioCorreoEntity;
import com.prisaly.m7.infrastructure.M7Configuration;
import com.prisaly.m7.infrastructure.NotificacionM4Adapter;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.aop.support.AopUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.persistence.autoconfigure.EntityScan;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.postgresql.PostgreSQLContainer;

/**
 * Integración real M4–M7 sobre PostgreSQL. {@link NotificacionPort} lo aporta {@link M7Configuration};
 * {@code @EnableAutoConfiguration} incluye el fallback de M4, que debe quedar desactivado.
 */
@SpringBootTest(classes = M7ConvocatoriaPostgreSqlIT.TestApplication.class)
class M7ConvocatoriaPostgreSqlIT {

    static final PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:16.15");

    static {
        postgres.start();
    }

    @DynamicPropertySource
    static void propiedades(DynamicPropertyRegistry r) {
        r.add("spring.datasource.url", postgres::getJdbcUrl);
        r.add("spring.datasource.username", postgres::getUsername);
        r.add("spring.datasource.password", postgres::getPassword);
        r.add("spring.jpa.hibernate.ddl-auto", () -> "validate");
        r.add("spring.jpa.properties.hibernate.globally_quoted_identifiers", () -> "true");
        r.add(
                "spring.flyway.locations",
                () -> "filesystem:" + Path.of("../app/src/main/resources/db/migration").toAbsolutePath());
    }

    @Autowired
    SesionAcademicaService service;

    @Autowired
    NotificacionPort notificaciones;

    @Autowired
    NotificarConvocatoriaService notificarConvocatoria;

    @Autowired
    RegistroAvisoPort registroAviso;

    @Autowired
    RegistroEnvioCorreoPort registroEnvio;

    @Autowired
    JdbcTemplate jdbc;

    @Autowired
    PlatformTransactionManager txManager;

    @Autowired
    ApplicationContext contexto;

    UUID docente;
    UUID estudiante;
    UUID usuario;
    UUID caso;
    UUID sesion;

    @BeforeEach
    void preparar() {
        jdbc.execute("TRUNCATE TABLE \"Rol\" RESTART IDENTITY CASCADE");
        docente = UUID.randomUUID();
        usuario = UUID.randomUUID();
        estudiante = UUID.randomUUID();
        caso = UUID.randomUUID();
        jdbc.update("INSERT INTO \"Rol\" (\"idRol\",\"nombre\",\"descripcion\") VALUES (1,'Fixture','Test')");
        usuario(docente);
        usuario(usuario);
        jdbc.update("INSERT INTO \"Estudiante\" (\"idEstudiante\",\"idUsuario\") VALUES (?,?)", estudiante, usuario);
        jdbc.update(
                """
                INSERT INTO "Caso" ("idCaso","idDocente","nombre","tiempoEstimadoSegundos","estado")
                VALUES (?,?,'Caso test',60,'Publicado')
                """,
                caso,
                docente);
        Instant inicio = Instant.now().plusSeconds(86400);
        sesion = service.crearSesion(docente, caso, "Espacio test", null, inicio, inicio.plusSeconds(3600), 600)
                .getId();
    }

    void usuario(UUID id) {
        jdbc.update(
                """
                INSERT INTO "Usuario" ("idUsuario","idRol","nombre","correo") VALUES (?,1,'Fixture',?)
                """,
                id,
                id + "@example.invalid");
    }

    int cantidad(String tabla) {
        return jdbc.queryForObject("SELECT count(*) FROM \"" + tabla + "\"", Integer.class);
    }

    TransactionTemplate tx() {
        return new TransactionTemplate(txManager);
    }

    /** Counts through a fresh JDBC connection, outside Spring's pool and transaction. */
    int cantidadDesdeOtraConexion(String tabla) throws SQLException {
        try (Connection conexion = DriverManager.getConnection(
                        postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword());
                var consulta = conexion.createStatement();
                var resultado = consulta.executeQuery("SELECT count(*) FROM \"" + tabla + "\"")) {
            assertTrue(resultado.next());
            return resultado.getInt(1);
        }
    }

    void assertSinFilasM4M7() throws SQLException {
        assertEquals(0, cantidadDesdeOtraConexion("Convocatoria"));
        assertEquals(0, cantidadDesdeOtraConexion("Aviso"));
        assertEquals(0, cantidadDesdeOtraConexion("EnvioCorreo"));
    }

    void assertRechazoProveedorConCausa(Runnable accion) {
        var rechazo = assertThrows(ReglaNegocioException.class, accion::run);
        assertEquals("M4-PROVEEDOR-NO-DISPONIBLE", rechazo.getCodigo());
        assertNotNull(rechazo.getCause());
    }

    @Test
    void unicoProveedorNotificacionPortEsAdaptadorM7() {
        Map<String, NotificacionPort> proveedores = contexto.getBeansOfType(NotificacionPort.class);
        assertEquals(1, proveedores.size());
        assertTrue(proveedores.containsKey("notificacionM7"));
        assertEquals(NotificacionM4Adapter.class, AopUtils.getTargetClass(notificaciones));
        assertFalse(contexto.containsBean("notificacionesNoDisponibles"));
    }

    @Test
    void convocatoriaAvisoYCorreoSeConfirmanJuntos() throws SQLException {
        var convocatoria = service.convocarEstudiante(docente, sesion, estudiante);
        assertEquals(1, cantidadDesdeOtraConexion("Convocatoria"));
        assertEquals(1, cantidadDesdeOtraConexion("Aviso"));
        assertEquals(1, cantidadDesdeOtraConexion("EnvioCorreo"));
        assertEquals(
                convocatoria.getId(),
                jdbc.queryForObject("SELECT \"idConvocatoria\" FROM \"EnvioCorreo\"", UUID.class));
    }

    @Test
    void falloAlRegistrarAvisoRevierteConvocatoria() throws SQLException {
        jdbc.execute("ALTER TABLE \"Aviso\" ADD CONSTRAINT prueba_m4m7_fallo_aviso CHECK (false)");
        try {
            assertRechazoProveedorConCausa(() -> service.convocarEstudiante(docente, sesion, estudiante));
            assertSinFilasM4M7();
        } finally {
            jdbc.execute("ALTER TABLE \"Aviso\" DROP CONSTRAINT IF EXISTS prueba_m4m7_fallo_aviso");
        }
    }

    @Test
    void falloAlEncolarCorreoRevierteConvocatoriaYAviso() throws SQLException {
        jdbc.execute("ALTER TABLE \"EnvioCorreo\" ADD CONSTRAINT prueba_m4m7_fallo_envio CHECK (false)");
        try {
            assertRechazoProveedorConCausa(() -> service.convocarEstudiante(docente, sesion, estudiante));
            assertSinFilasM4M7();
        } finally {
            jdbc.execute("ALTER TABLE \"EnvioCorreo\" DROP CONSTRAINT IF EXISTS prueba_m4m7_fallo_envio");
        }
    }

    @Test
    void falloPosteriorEnLaTransaccionExteriorRevierteTodo() throws SQLException {
        assertThrows(
                IllegalStateException.class,
                () -> tx().executeWithoutResult(s -> {
                    service.convocarEstudiante(docente, sesion, estudiante);
                    assertEquals(1, cantidad("Aviso"));
                    throw new IllegalStateException("forced failure after M7 registration");
                }));
        assertSinFilasM4M7();
    }

    @Test
    void convocatoriaRegistraAvisoYEncolaCorreoPendiente() {
        var convocatoria = service.convocarEstudiante(docente, sesion, estudiante);
        assertEquals(1, cantidad("Convocatoria"));
        assertEquals(1, cantidad("Aviso"));
        assertEquals(1, cantidad("EnvioCorreo"));
        Map<String, Object> aviso = jdbc.queryForMap("SELECT * FROM \"Aviso\"");
        assertEquals(usuario, aviso.get("idUsuarioDestino"));
        assertEquals("convocatoria:" + convocatoria.getId(), aviso.get("claveEvento"));
        assertEquals("Convocatoria", aviso.get("tipo"));
        assertNull(aviso.get("idParticipacion"));
        Map<String, Object> envio = jdbc.queryForMap("SELECT * FROM \"EnvioCorreo\"");
        assertEquals(usuario, envio.get("idUsuarioDestino"));
        assertEquals(convocatoria.getId(), envio.get("idConvocatoria"));
        assertEquals(usuario + "@example.invalid", envio.get("correoDestino"));
        assertEquals("convocatoria:" + convocatoria.getId(), envio.get("claveIdempotencia"));
        assertEquals("Pendiente", envio.get("estadoEnvio"));
        assertEquals(0, ((Number) envio.get("numeroIntentos")).intValue());
        assertNull(envio.get("fechaEnvio"));
    }

    @Test
    void exitoSignificaEncoladoNoEntrega() {
        service.convocarEstudiante(docente, sesion, estudiante);
        assertNull(jdbc.queryForObject("SELECT \"fechaEnvio\" FROM \"EnvioCorreo\"", Object.class));
        assertEquals("Pendiente", jdbc.queryForObject("SELECT \"estadoEnvio\" FROM \"EnvioCorreo\"", String.class));
    }

    @Test
    void dobleRegistroMismaClaveYContenidoNoDuplica() {
        service.convocarEstudiante(docente, sesion, estudiante);
        Map<String, Object> avisoPrevio = jdbc.queryForMap("SELECT * FROM \"Aviso\"");
        Map<String, Object> envioPrevio = jdbc.queryForMap("SELECT * FROM \"EnvioCorreo\"");
        tx().executeWithoutResult(s -> notificaciones.notificarConvocatoria(sesion, estudiante));
        assertEquals(1, cantidad("Aviso"));
        assertEquals(1, cantidad("EnvioCorreo"));
        assertEquals(avisoPrevio, jdbc.queryForMap("SELECT * FROM \"Aviso\""));
        assertEquals(envioPrevio, jdbc.queryForMap("SELECT * FROM \"EnvioCorreo\""));
    }

    @Test
    void mismaClaveContenidoIncompatibleRechazaAviso() {
        service.convocarEstudiante(docente, sesion, estudiante);
        UUID convocatoriaId = jdbc.queryForObject("SELECT \"idConvocatoria\" FROM \"Convocatoria\"", UUID.class);
        var evento = new ConvocatoriaNotificable(
                convocatoriaId, sesion, estudiante, usuario, usuario + "@example.invalid");
        assertThrows(
                RuntimeException.class,
                () -> tx().executeWithoutResult(
                        s -> registroAviso.registrar(new AvisoConvocatoria(evento, "contenido distinto"))));
    }

    @Test
    void mismaClaveContenidoIncompatibleRechazaEnvio() {
        service.convocarEstudiante(docente, sesion, estudiante);
        UUID convocatoriaId = jdbc.queryForObject("SELECT \"idConvocatoria\" FROM \"Convocatoria\"", UUID.class);
        var evento = new ConvocatoriaNotificable(
                convocatoriaId, sesion, estudiante, usuario, usuario + "@example.invalid");
        assertThrows(
                RuntimeException.class,
                () -> tx().executeWithoutResult(s -> registroEnvio.encolar(
                        new EnvioCorreoConvocatoria(evento, "Asunto otro", "contenido otro"))));
    }

    @Test
    void rollbackSiFallaEncoladoTrasAviso() {
        service.convocarEstudiante(docente, sesion, estudiante);
        jdbc.update("DELETE FROM \"Aviso\"");
        jdbc.update("DELETE FROM \"EnvioCorreo\"");
        jdbc.execute("ALTER TABLE \"EnvioCorreo\" ADD CONSTRAINT prueba_fallo_envio CHECK (false)");
        try {
            assertThrows(
                    RuntimeException.class,
                    () -> tx().executeWithoutResult(
                            s -> notificarConvocatoria.ejecutar(
                                    new NotificarConvocatoriaCommand(sesion, estudiante))));
            assertEquals(0, cantidad("Aviso"));
            assertEquals(0, cantidad("EnvioCorreo"));
        } finally {
            jdbc.execute("ALTER TABLE \"EnvioCorreo\" DROP CONSTRAINT prueba_fallo_envio");
        }
    }

    @Test
    void rechazoDeM4NoGeneraAvisoNiCorreo() {
        assertThrows(ReglaNegocioException.class, () -> service.convocarEstudiante(null, sesion, estudiante));
        assertEquals(0, cantidad("Convocatoria"));
        assertEquals(0, cantidad("Aviso"));
        assertEquals(0, cantidad("EnvioCorreo"));
    }

    @Test
    void integridadReferencialDeAviso() {
        var evento = new ConvocatoriaNotificable(
                UUID.randomUUID(), sesion, estudiante, UUID.randomUUID(), "x@example.invalid");
        assertThrows(
                RuntimeException.class,
                () -> tx().executeWithoutResult(
                        s -> registroAviso.registrar(new AvisoConvocatoria(evento, "Aviso"))));
        assertEquals(0, cantidad("Aviso"));
    }

    @Test
    void dosRegistrosConcurrentesDelMismoEventoNoDuplican() throws Exception {
        service.convocarEstudiante(docente, sesion, estudiante);
        jdbc.update("DELETE FROM \"Aviso\"");
        jdbc.update("DELETE FROM \"EnvioCorreo\"");
        var inicio = new CountDownLatch(1);
        try (var pool = Executors.newFixedThreadPool(2)) {
            Callable<Void> tarea = () -> {
                assertTrue(inicio.await(10, TimeUnit.SECONDS));
                tx().executeWithoutResult(s -> notificaciones.notificarConvocatoria(sesion, estudiante));
                return null;
            };
            var a = pool.submit(tarea);
            var b = pool.submit(tarea);
            inicio.countDown();
            a.get(30, TimeUnit.SECONDS);
            b.get(30, TimeUnit.SECONDS);
        }
        assertEquals(1, cantidad("Aviso"));
        assertEquals(1, cantidad("EnvioCorreo"));
    }

    @SpringBootConfiguration
    @EnableAutoConfiguration
    @EntityScan(basePackageClasses = {ConvocatoriaEntity.class, AvisoEntity.class, EnvioCorreoEntity.class})
    @EnableJpaRepositories(basePackageClasses = JpaConvocatoriaRepository.class)
    @Import({
        M7Configuration.class,
        SesionAcademicaService.class,
        SesionRepositoryAdapter.class,
        ConvocatoriaRepositoryAdapter.class
    })
    static class TestApplication {
        @Bean
        IdentidadPort identidadDePrueba() {
            return (id, permiso) -> true;
        }

    }
}
