package com.prisaly.m7;

import static org.junit.jupiter.api.Assertions.*;

import com.icegreen.greenmail.util.GreenMail;
import com.icegreen.greenmail.util.ServerSetup;
import com.prisaly.m4.application.port.IdentidadPort;
import com.prisaly.m4.application.port.NotificacionPort;
import com.prisaly.m4.application.service.SesionAcademicaService;
import com.prisaly.m4.infrastructure.adapter.ConvocatoriaRepositoryAdapter;
import com.prisaly.m4.infrastructure.adapter.SesionRepositoryAdapter;
import com.prisaly.m4.infrastructure.persistence.ConvocatoriaEntity;
import com.prisaly.m4.infrastructure.persistence.JpaConvocatoriaRepository;
import com.prisaly.m7.application.PoliticaReintentos;
import com.prisaly.m7.application.ProcesarEnviosPendientesService;
import com.prisaly.m7.application.ProveedorCorreoPort;
import com.prisaly.m7.application.ReclamoEnvioPort;
import com.prisaly.m7.application.RegistroEnvioCorreoPort;
import com.prisaly.m7.domain.ClaseErrorCorreo;
import com.prisaly.m7.domain.EnvioReclamado;
import com.prisaly.m7.domain.EstadoEnvioCorreo;
import com.prisaly.m7.domain.ResultadoEnvioSmtp;
import com.prisaly.m7.infrastructure.AvisoEntity;
import com.prisaly.m7.infrastructure.EnvioCorreoEntity;
import com.prisaly.m7.infrastructure.JavaMailProveedorCorreoAdapter;
import com.prisaly.m7.infrastructure.M7Configuration;
import com.prisaly.m7.infrastructure.ReclamoEnvioJpaAdapter;
import jakarta.mail.internet.MimeMessage;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.persistence.autoconfigure.EntityScan;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mail.javamail.JavaMailSenderImpl;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.postgresql.PostgreSQLContainer;

@SpringBootTest(classes = M7EnvioWorkerPostgreSqlIT.TestApplication.class)
class M7EnvioWorkerPostgreSqlIT {

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
    ReclamoEnvioPort reclamo;

    @Autowired
    RegistroEnvioCorreoPort registroEnvio;

    @Autowired
    NotificacionPort notificaciones;

    @Autowired
    JdbcTemplate jdbc;

    @Autowired
    PlatformTransactionManager txManager;

    GreenMail greenMail;
    JavaMailSenderImpl mailSender;
    ProveedorCorreoPort smtpReal;

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

        ServerSetup setup = new ServerSetup(0, "127.0.0.1", ServerSetup.PROTOCOL_SMTP);
        greenMail = new GreenMail(setup);
        greenMail.start();
        mailSender = new JavaMailSenderImpl();
        mailSender.setHost("127.0.0.1");
        mailSender.setPort(greenMail.getSmtp().getPort());
        smtpReal = new JavaMailProveedorCorreoAdapter(mailSender);
    }

    @AfterEach
    void detenerSmtp() {
        if (greenMail != null) {
            greenMail.stop();
        }
    }

    void usuario(UUID id) {
        jdbc.update(
                """
                INSERT INTO "Usuario" ("idUsuario","idRol","nombre","correo") VALUES (?,1,'Fixture',?)
                """,
                id,
                id + "@example.invalid");
    }

    TransactionTemplate tx() {
        return new TransactionTemplate(txManager);
    }

    ProcesarEnviosPendientesService worker(ProveedorCorreoPort proveedor) {
        return new ProcesarEnviosPendientesService(
                reclamo,
                proveedor,
                PoliticaReintentos.ratificada(),
                "noreply@prisaly.test",
                10,
                Duration.ofSeconds(15));
    }

    @Test
    void smtpAceptadoPasaAEnviado() throws Exception {
        service.convocarEstudiante(docente, sesion, estudiante);
        assertEquals(1, worker(smtpReal).procesarLote());
        Map<String, Object> envio = jdbc.queryForMap("SELECT * FROM \"EnvioCorreo\"");
        assertEquals(EstadoEnvioCorreo.ENVIADO, envio.get("estadoEnvio"));
        assertNotNull(envio.get("fechaEnvio"));
        assertEquals(1, ((Number) envio.get("numeroIntentos")).intValue());
        assertTrue(greenMail.waitForIncomingEmail(5_000, 1));
        MimeMessage[] mensajes = greenMail.getReceivedMessages();
        assertEquals(1, mensajes.length);
        assertEquals(usuario + "@example.invalid", mensajes[0].getAllRecipients()[0].toString());
    }

    @Test
    void errorTransitorioProgramaReintento() {
        service.convocarEstudiante(docente, sesion, estudiante);
        ProveedorCorreoPort fallido = (r, d, a, c) -> new ResultadoEnvioSmtp.Rechazado(
                ClaseErrorCorreo.TRANSITORIO, "421", "timeout");
        worker(fallido).procesarLote();
        Map<String, Object> envio = jdbc.queryForMap("SELECT * FROM \"EnvioCorreo\"");
        assertEquals(EstadoEnvioCorreo.PENDIENTE, envio.get("estadoEnvio"));
        assertEquals(1, ((Number) envio.get("numeroIntentos")).intValue());
        assertNotNull(envio.get("proximoReintento"));
        assertTrue(envio.get("ultimoError").toString().contains("421"));
    }

    @Test
    void errorPermanenteMarcaFallido() {
        service.convocarEstudiante(docente, sesion, estudiante);
        ProveedorCorreoPort fallido = (r, d, a, c) -> new ResultadoEnvioSmtp.Rechazado(
                ClaseErrorCorreo.PERMANENTE_DESTINATARIO, "550", "User unknown");
        worker(fallido).procesarLote();
        assertEquals(
                EstadoEnvioCorreo.FALLIDO,
                jdbc.queryForObject("SELECT \"estadoEnvio\" FROM \"EnvioCorreo\"", String.class));
    }

    @Test
    void configuracionNoMarcaFallido() {
        service.convocarEstudiante(docente, sesion, estudiante);
        ProveedorCorreoPort fallido = (r, d, a, c) -> new ResultadoEnvioSmtp.Rechazado(
                ClaseErrorCorreo.CONFIGURACION, "535", "Authentication failed");
        worker(fallido).procesarLote();
        assertEquals(
                EstadoEnvioCorreo.PENDIENTE,
                jdbc.queryForObject("SELECT \"estadoEnvio\" FROM \"EnvioCorreo\"", String.class));
    }

    @Test
    void agotamientoDeIntentosMarcaFallido() {
        service.convocarEstudiante(docente, sesion, estudiante);
        jdbc.update("UPDATE \"EnvioCorreo\" SET \"numeroIntentos\" = 4");
        ProveedorCorreoPort fallido = (r, d, a, c) -> new ResultadoEnvioSmtp.Rechazado(
                ClaseErrorCorreo.TRANSITORIO, "421", "timeout");
        worker(fallido).procesarLote();
        Map<String, Object> envio = jdbc.queryForMap("SELECT * FROM \"EnvioCorreo\"");
        assertEquals(EstadoEnvioCorreo.FALLIDO, envio.get("estadoEnvio"));
        assertEquals(5, ((Number) envio.get("numeroIntentos")).intValue());
    }

    @Test
    void dosWorkersConcurrentesNoDuplicanEnvioSmtp() throws Exception {
        service.convocarEstudiante(docente, sesion, estudiante);
        AtomicInteger envios = new AtomicInteger();
        CountDownLatch listos = new CountDownLatch(2);
        CountDownLatch liberar = new CountDownLatch(1);
        ProveedorCorreoPort lento = (r, d, a, c) -> {
            envios.incrementAndGet();
            listos.countDown();
            try {
                assertTrue(liberar.await(10, TimeUnit.SECONDS));
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException(e);
            }
            return new ResultadoEnvioSmtp.Aceptado("<concurrent@test>");
        };
        try (var pool = Executors.newFixedThreadPool(2)) {
            Callable<Integer> tarea = () -> worker(lento).procesarLote();
            var a = pool.submit(tarea);
            var b = pool.submit(tarea);
            assertTrue(listos.await(2, TimeUnit.SECONDS) || envios.get() >= 1);
            liberar.countDown();
            int total = a.get(30, TimeUnit.SECONDS) + b.get(30, TimeUnit.SECONDS);
            assertEquals(1, total);
        }
        assertEquals(1, envios.get());
        assertEquals(
                EstadoEnvioCorreo.ENVIADO,
                jdbc.queryForObject("SELECT \"estadoEnvio\" FROM \"EnvioCorreo\"", String.class));
    }

    @Test
    void leaseVencidoPermiteRecuperacion() {
        service.convocarEstudiante(docente, sesion, estudiante);
        List<EnvioReclamado> primero = tx().execute(s -> reclamo.reclamar(1, Duration.ofSeconds(1), 5));
        assertEquals(1, primero.size());
        jdbc.update(
                "UPDATE \"EnvioCorreo\" SET \"proximoReintento\" = now() - interval '1 second'");
        assertEquals(1, worker(smtpReal).procesarLote());
        assertEquals(
                EstadoEnvioCorreo.ENVIADO,
                jdbc.queryForObject("SELECT \"estadoEnvio\" FROM \"EnvioCorreo\"", String.class));
        assertEquals(2, jdbc.queryForObject("SELECT \"numeroIntentos\" FROM \"EnvioCorreo\"", Integer.class));
    }

    @Test
    void resultadoSinLeaseNoActualiza() {
        service.convocarEstudiante(docente, sesion, estudiante);
        List<EnvioReclamado> reclamados = tx().execute(s -> reclamo.reclamar(1, Duration.ofSeconds(30), 5));
        EnvioReclamado original = reclamados.getFirst();
        jdbc.update(
                "UPDATE \"EnvioCorreo\" SET \"proximoReintento\" = now() + interval '1 hour'");
        boolean guardado = tx().execute(s -> reclamo.registrarAceptacion(
                original.idEnvio(),
                new ReclamoEnvioPort.InstantLease(original.leaseHasta(), original.numeroIntentos()),
                "<lost@test>"));
        assertFalse(guardado);
        assertEquals(
                EstadoEnvioCorreo.PENDIENTE,
                jdbc.queryForObject("SELECT \"estadoEnvio\" FROM \"EnvioCorreo\"", String.class));
        assertNull(jdbc.queryForObject("SELECT \"fechaEnvio\" FROM \"EnvioCorreo\"", Instant.class));
    }

    @Test
    void reconvocarEnviadoEsNoOp() {
        service.convocarEstudiante(docente, sesion, estudiante);
        worker(smtpReal).procesarLote();
        assertEquals(EstadoEnvioCorreo.ENVIADO, jdbc.queryForObject("SELECT \"estadoEnvio\" FROM \"EnvioCorreo\"", String.class));
        tx().executeWithoutResult(s -> notificaciones.notificarConvocatoria(sesion, estudiante));
        assertEquals(1, jdbc.queryForObject("SELECT count(*) FROM \"EnvioCorreo\"", Integer.class));
        assertEquals(EstadoEnvioCorreo.ENVIADO, jdbc.queryForObject("SELECT \"estadoEnvio\" FROM \"EnvioCorreo\"", String.class));
    }

    @Test
    void reconvocarFallidoNoReactiva() {
        service.convocarEstudiante(docente, sesion, estudiante);
        jdbc.update(
                "UPDATE \"EnvioCorreo\" SET \"estadoEnvio\" = 'Fallido', \"numeroIntentos\" = 5, \"ultimoError\" = 'x'");
        tx().executeWithoutResult(s -> notificaciones.notificarConvocatoria(sesion, estudiante));
        assertEquals(EstadoEnvioCorreo.FALLIDO, jdbc.queryForObject("SELECT \"estadoEnvio\" FROM \"EnvioCorreo\"", String.class));
        assertEquals(5, jdbc.queryForObject("SELECT \"numeroIntentos\" FROM \"EnvioCorreo\"", Integer.class));
    }

    @Test
    void leaseAgotadoSinResultadoPasaAFallido() {
        service.convocarEstudiante(docente, sesion, estudiante);
        jdbc.update(
                """
                UPDATE "EnvioCorreo"
                SET "numeroIntentos" = 5,
                    "proximoReintento" = now() - interval '1 second',
                    "estadoEnvio" = 'Pendiente'
                """);
        assertEquals(0, worker(smtpReal).procesarLote());
        assertEquals(
                EstadoEnvioCorreo.FALLIDO,
                jdbc.queryForObject("SELECT \"estadoEnvio\" FROM \"EnvioCorreo\"", String.class));
        assertEquals(0, greenMail.getReceivedMessages().length);
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

        @Bean
        ReclamoEnvioPort reclamoEnvioDePrueba(jakarta.persistence.EntityManager em) {
            return new ReclamoEnvioJpaAdapter(em);
        }
    }
}
