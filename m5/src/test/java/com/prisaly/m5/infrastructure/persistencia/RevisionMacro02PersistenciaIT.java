package com.prisaly.m5.infrastructure.persistencia;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.prisaly.m4.infrastructure.adapter.ProgramacionParticipacionJdbcAdapter;
import com.prisaly.m5.application.ReferenciaIntento;
import com.prisaly.m5.application.acceso.AccesoProtegidoParticipacion;
import com.prisaly.m5.application.acceso.ControlAccesoIntentos;
import com.prisaly.m5.application.acceso.IdentidadEstudianteActual;
import com.prisaly.m5.application.acceso.IdentificadorEstudiante;
import com.prisaly.m5.application.acceso.Titularidad;
import com.prisaly.m5.application.acceso.TitularidadIntentos;
import com.prisaly.m5.application.port.RepositorioIntentos;
import com.prisaly.m5.infrastructure.FachadaParticipacionTransaccional;
import com.prisaly.m5.infrastructure.evaluacion.FabricaConsultaIntentoEvaluacion;
import com.prisaly.m5.infrastructure.programacion.TitularidadIntentosDesdeM4;
import com.prisaly.m5.presentation.TraductorErroresM5;
import com.prisaly.m6.application.PreparacionContextoException;
import com.prisaly.m6.application.PreparacionContextoException.Codigo;
import com.prisaly.m6.application.port.AutorizacionEvaluacionPort.DecisionAcceso;
import com.prisaly.m6.application.port.ConsultaContextoAcademicoPort;
import com.prisaly.m6.application.port.ConsultaIntentoEvaluacionPort;
import com.prisaly.m6.application.port.SolicitudCapacidadContextoAcademicoPort;
import com.prisaly.m6.application.service.PrepararContextoEvaluacionService;
import com.prisaly.m6.domain.strategy.SolicitudEvaluacion;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

/** Review of DC-M5M6-MACRO-02 over PostgreSQL 16.15: rollback, real 409 and consumer behaviour of an absent link. */
@SpringBootTest(classes = RepositorioIntentosJpaIT.AppPrueba.class)
@Testcontainers
class RevisionMacro02PersistenciaIT {
    @Container static PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:16.15");

    @DynamicPropertySource static void propiedades(DynamicPropertyRegistry r) {
        r.add("spring.datasource.url", postgres::getJdbcUrl);
        r.add("spring.datasource.username", postgres::getUsername);
        r.add("spring.datasource.password", postgres::getPassword);
        r.add("spring.datasource.hikari.connection-init-sql", () -> "SET lock_timeout = '400ms'");
        r.add("spring.jpa.hibernate.ddl-auto", () -> "validate");
        r.add("spring.jpa.properties.hibernate.globally_quoted_identifiers", () -> "true");
        r.add("spring.flyway.locations", RepositorioIntentosJpaIT::ubicacionFlywayCanonica);
    }

    @Autowired JdbcTemplate jdbc;
    @Autowired FachadaParticipacionTransaccional fachada;
    @Autowired RepositorioIntentos repositorio;
    @Autowired PlatformTransactionManager tx;
    CadenaIntentoPrueba cadena;

    @BeforeEach void crear() { cadena = CadenaIntentoPrueba.crear(jdbc); }

    /** The unit of work inserts one answer and then blocks updating another: the insert must not survive. */
    @Test void falloTecnicoAMitadDeUnidadDeTrabajoNoDejaEscriturasParciales() throws Exception {
        fachada.guardarRespuesta(cadena.intento(), cadena.pregunta1(), cadena.opcion1a(), 0);
        var antes = huella();
        var retenido = new CountDownLatch(1);
        var liberar = new CountDownLatch(1);
        var retenedor = CompletableFuture.runAsync(() -> new TransactionTemplate(tx).executeWithoutResult(s -> {
            jdbc.queryForList("SELECT 1 FROM \"Respuesta\" WHERE \"idIntento\"=? FOR UPDATE", cadena.idIntento());
            retenido.countDown();
            esperar(liberar);
        }));
        assertTrue(retenido.await(10, TimeUnit.SECONDS));
        RuntimeException fallo;
        try {
            fallo = assertThrows(RuntimeException.class, () -> new TransactionTemplate(tx).executeWithoutResult(s -> {
                var intento = repositorio.buscar(cadena.intento()).orElseThrow();
                intento.registrarRespuesta(cadena.pregunta2(), cadena.opcion2a());
                intento.actualizarRespuesta(cadena.pregunta1(), cadena.opcion1b(), true);
                repositorio.guardar(cadena.intento(), intento);
            }));
        } finally {
            liberar.countDown();
        }
        retenedor.get(30, TimeUnit.SECONDS);
        // Hibernate flushes inserts before updates: failing on the UPDATE means the INSERT had already run.
        assertTrue(fallo.getMessage().toLowerCase().contains("update"), fallo.getMessage());
        var http = TraductorErroresM5.traducir(fallo).orElseThrow();
        assertEquals(500, http.estado(), fallo.getClass().getName());
        assertEquals("ERROR_INTERNO", http.cuerpo().code());
        assertFalse(http.cuerpo().message().contains("Respuesta"));
        assertEquals(antes, huella(), "no partial writes after rollback");
    }

    @Test void violacionesRealesDeIntegridadConservanEl409SinEscribir() {
        var otra = CadenaIntentoPrueba.crear(jdbc);
        var antes = huella();
        // Composite FK (option of another question) and trigger tr_respuesta_caso (question of another case).
        for (Runnable escritura : List.<Runnable>of(
                () -> fachada.guardarRespuesta(cadena.intento(), cadena.pregunta1(), cadena.opcion2a(), 0),
                () -> fachada.guardarRespuesta(cadena.intento(), otra.pregunta1(), otra.opcion1a(), 0))) {
            RuntimeException error = assertThrows(RuntimeException.class, escritura::run);
            assertInstanceOf(DataIntegrityViolationException.class, error);
            var http = TraductorErroresM5.traducir(error).orElseThrow();
            assertEquals(409, http.estado());
            assertEquals("CONFLICTO_DATOS", http.cuerpo().code());
        }
        assertEquals(antes, huella());
    }

    @Test void intentoInexistenteEsAusenciaParaElConsumidor() {
        UUID ausente = UUID.randomUUID();
        var titularidad = new TitularidadIntentosDesdeM4(jdbc, new ProgramacionParticipacionJdbcAdapter(jdbc));
        assertEquals(Optional.empty(), lector(ausente, titularidad).findById(ausente));
        assertEquals(Codigo.INTENTO_INEXISTENTE, codigo(ausente, titularidad));
    }

    /** Characterization: the real ownership query inner-joins too, so a corrupt orphan looks absent to the consumer. */
    @Test void huerfanoConTitularidadRealSeVeComoAusencia() {
        UUID huerfano = crearHuerfano();
        var titularidad = new TitularidadIntentosDesdeM4(jdbc, new ProgramacionParticipacionJdbcAdapter(jdbc));
        assertEquals(Optional.empty(), lector(huerfano, titularidad).findById(huerfano));
        assertEquals(Codigo.INTENTO_INEXISTENTE, codigo(huerfano, titularidad));
    }

    @Test void vinculoAusenteTrasPasarTitularidadEsInconsistenciaNoAusencia() {
        UUID huerfano = crearHuerfano();
        var propio = mock(TitularidadIntentos.class);
        when(propio.de(any(), any())).thenReturn(Titularidad.PROPIO);
        var error = assertThrows(IllegalStateException.class, () -> lector(huerfano, propio).findById(huerfano));
        assertTrue(error.getMessage().contains("inconsistentes"));
        assertEquals(Codigo.FALLO_CONSULTA_INTENTO, codigo(huerfano, propio));
    }

    private UUID crearHuerfano() {
        UUID id = UUID.randomUUID();
        new TransactionTemplate(tx).executeWithoutResult(s -> {
            // Bypasses FK and triggers only in this throwaway container, to materialize corrupt data.
            jdbc.execute("SET LOCAL session_replication_role = replica");
            jdbc.update("INSERT INTO \"Intento\" (\"idIntento\",\"idParticipacion\",\"numeroIntento\",\"inicio\",\"limiteTemporal\",\"estado\") VALUES (?,?,1,now(),now()+interval '1 hour','EnCurso')",
                    id, UUID.randomUUID());
        });
        return id;
    }

    private ConsultaIntentoEvaluacionPort lector(UUID id, TitularidadIntentos titularidad) {
        IdentidadEstudianteActual identidad = () -> Optional.of(new IdentificadorEstudiante(cadena.estudiante().toString()));
        var protegida = new AccesoProtegidoParticipacion(new ControlAccesoIntentos(identidad, titularidad), fachada);
        return new FabricaConsultaIntentoEvaluacion(s -> DecisionAcceso.AUTORIZADO, protegida, repositorio, identidad,
                new ProgramacionParticipacionJdbcAdapter(jdbc), tx).para(new SolicitudEvaluacion(id, UUID.randomUUID()));
    }

    private Codigo codigo(UUID id, TitularidadIntentos titularidad) {
        return assertThrows(PreparacionContextoException.class, () -> new PrepararContextoEvaluacionService(lector(id, titularidad),
                mock(SolicitudCapacidadContextoAcademicoPort.class), mock(ConsultaContextoAcademicoPort.class)).preparar(id)).codigo();
    }

    private List<Map<String, Object>> huella() {
        var filas = new java.util.ArrayList<>(jdbc.queryForList("SELECT * FROM \"Respuesta\" WHERE \"idIntento\"=? ORDER BY \"idPregunta\"", cadena.idIntento()));
        filas.addAll(jdbc.queryForList("SELECT * FROM \"Intento\" WHERE \"idIntento\"=?", cadena.idIntento()));
        return filas;
    }

    private static void esperar(CountDownLatch latch) {
        try {
            assertTrue(latch.await(30, TimeUnit.SECONDS));
        } catch (InterruptedException e) {
            throw new IllegalStateException(e);
        }
    }
}
