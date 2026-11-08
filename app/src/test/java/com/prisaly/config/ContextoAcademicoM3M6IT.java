package com.prisaly.config;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.prisaly.m3.api.AccesoContextoAcademicoDenegadoException;
import com.prisaly.m3.api.ContextoAcademicoConsultaPort;
import com.prisaly.m3.application.port.AutorizacionContextoAcademicoPort;
import com.prisaly.m3.application.port.AutorizacionContextoAcademicoPort.Decision;
import com.prisaly.m3.application.port.AutorizacionContextoAcademicoPort.Operacion;
import com.prisaly.m3.infrastructure.persistence.jpa.CasoJpaAggregateReader;
import com.prisaly.m3.infrastructure.persistence.jpa.ContextoAcademicoProtectedReader;
import com.prisaly.m6.application.AccesoEvaluacionDenegadoException;
import com.prisaly.m6.application.PreparacionContextoException;
import com.prisaly.m6.application.PreparacionContextoException.Codigo;
import com.prisaly.m6.application.integration.ContextoAcademico;
import com.prisaly.m6.application.integration.IntentoEvaluacion;
import com.prisaly.m6.application.integration.IntentoEvaluacion.RespuestaReferencia;
import com.prisaly.m6.application.port.ConsultaContextoAcademicoPort;
import com.prisaly.m6.application.port.ConsultaIntentoEvaluacionPort;
import com.prisaly.m6.application.port.SolicitudCapacidadContextoAcademicoPort;
import com.prisaly.m6.application.service.PrepararContextoEvaluacionService;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

/**
 * Real contractual composition M3 → M6 over PostgreSQL 16.15 and V001-V009.
 *
 * <p>Real components: the M3 protected producer {@link ContextoAcademicoProtectedReader} over the
 * application's own {@link CasoJpaAggregateReader} bean, the M3 port
 * {@link ContextoAcademicoConsultaPort#consultar(UUID, String)}, {@link MapeoContextoAcademicoM3}
 * and M6's {@link PrepararContextoEvaluacionService}.
 *
 * <p><b>CONTROLLED TEST AUTHORIZATION, NOT INSTITUTIONAL AUTHORIZATION.</b> The capability
 * verifier, the capability issuer, the M6 consumer adapter and the attempt reader are private test
 * doubles of this class. None is a Spring bean, none is a production provider, and the strings they
 * exchange are test fixtures, not M1 capabilities. No identity, role or permission is created.
 * Nothing here calculates, persists or publishes a grade.
 */
@SpringBootTest
@Testcontainers
@ExtendWith(OutputCaptureExtension.class)
class ContextoAcademicoM3M6IT {
    @Container static PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:16.15");

    @DynamicPropertySource
    static void propiedades(DynamicPropertyRegistry r) {
        r.add("spring.datasource.url", postgres::getJdbcUrl);
        r.add("spring.datasource.username", postgres::getUsername);
        r.add("spring.datasource.password", postgres::getPassword);
        r.add("spring.jpa.hibernate.ddl-auto", () -> "validate");
        r.add("spring.jpa.properties.hibernate.globally_quoted_identifiers", () -> "true");
        r.add("spring.flyway.enabled", () -> "true");
    }

    private static final Instant CREACION = Instant.parse("2026-01-01T01:01:01.111111Z");
    private static final Instant ACTUALIZACION = Instant.parse("2026-02-02T02:02:02.222222Z");

    @Autowired JdbcTemplate jdbc;
    @Autowired CasoJpaAggregateReader lectorJpa;

    private final Map<String, String> ids = new ConcurrentHashMap<>();
    private UUID caso, escenario, pregunta1, pregunta2, opcion1a, opcion1b, opcion2a, rubrica, asociacion1;
    private final String capacidadValida = "cap-controlada-" + UUID.randomUUID();
    private final String capacidadAjena = "cap-ajena-" + UUID.randomUUID();
    private final Set<String> revocadas = ConcurrentHashMap.newKeySet();
    private final List<Operacion> verificaciones = new ArrayList<>();

    @BeforeEach
    void sembrar() {
        caso = id("caso"); escenario = id("escenario"); pregunta1 = id("pregunta1"); pregunta2 = id("pregunta2");
        opcion1a = id("opcion1a"); opcion1b = id("opcion1b"); opcion2a = id("opcion2a");
        rubrica = id("rubrica"); asociacion1 = id("asociacion1");
        String sufijo = caso.toString().substring(0, 8);
        int rol = Math.abs(caso.hashCode() % 900_000_000) + 100_000;
        // Borrador with complete content and rubric, then published, then programmed: the official
        // V007 trigger sets fechaBloqueoAcademico. The three case dates end up different.
        jdbc.execute(sql("""
                INSERT INTO "Rol" ("idRol","nombre","descripcion") VALUES (%ROL%,'M3M6IT-%SUF%','Fixture M3M6');
                INSERT INTO "Usuario" ("idUsuario","idRol","nombre","correo")
                  VALUES ('%docente%',%ROL%,'Docente fixture M3M6','m3m6it-%SUF%@prisaly.test');
                INSERT INTO "Caso" ("idCaso","idDocente","nombre","historia","desarrolloSituacional",
                  "tiempoEstimadoSegundos","estado","fechaCreacion")
                  VALUES ('%caso%','%docente%','Caso M3M6 %SUF%','Historia','Desarrollo',900,'Borrador',
                  TIMESTAMPTZ '2026-01-01 01:01:01.111111+00');
                INSERT INTO "Escenario" ("idEscenario","idCaso","titulo","contexto","orden","activo")
                  VALUES ('%escenario%','%caso%','Escenario 1','Contexto',1,true);
                INSERT INTO "Pregunta" ("idPregunta","idEscenario","enunciado","estadoEdicion","orden")
                  VALUES ('%pregunta1%','%escenario%','Pregunta 1','Lista',1),
                         ('%pregunta2%','%escenario%','Pregunta 2','Lista',2);
                INSERT INTO "OpcionRespuesta" ("idOpcion","idPregunta","texto","esCorrecta","explicacion","orden")
                  VALUES ('%opcion1a%','%pregunta1%','Opcion 1A',true,'Explicacion 1A',1),
                         ('%opcion1b%','%pregunta1%','Opcion 1B',false,null,2),
                         ('%opcion2a%','%pregunta2%','Opcion 2A',true,null,1);
                INSERT INTO "Rubrica" ("idRubrica","idCaso","nombre","puntajeTotal","definicionEvaluacion")
                  VALUES ('%rubrica%','%caso%','Rubrica M3M6',10.00,'{"fixture":true}');
                INSERT INTO "RubricaPregunta" ("idRubricaPregunta","idRubrica","idPregunta","valorPregunta","criterio")
                  VALUES ('%asociacion1%','%rubrica%','%pregunta1%',6.00,'Criterio 1'),
                         ('%asociacion2%','%rubrica%','%pregunta2%',4.00,null);
                UPDATE "Caso" SET "estado"='Publicado', "fechaActualizacion"=TIMESTAMPTZ '2026-02-02 02:02:02.222222+00'
                  WHERE "idCaso"='%caso%';
                INSERT INTO "Sesion" ("idSesion","idCaso","idDocenteProgramador","nombreEspacioAcademico",
                  "inicioPrevisto","finPrevisto","tiempoMaximoParticipacionSegundos")
                  VALUES ('%sesion%','%caso%','%docente%','Espacio M3M6', now() + interval '1 day',
                  now() + interval '2 days', 900);
                """.replace("%ROL%", String.valueOf(rol)).replace("%SUF%", sufijo)));
    }

    // ------------------------------------------------------------------ valid capability

    @Test
    void capacidadValidaRecuperaContextoRealConFechasIntegridadYRubricaSinEscribir() {
        var antes = huella();
        var preparado = servicio(intentoFinalizado(caso), lectorM3(lectorJpa)).preparar(idIntento);
        ContextoAcademico c = preparado.contextoAcademico();
        assertEquals(List.of(Operacion.CONSULTAR_CONTEXTO_EVALUABLE), verificaciones, "verified once, exact operation");

        // Dates by name, against the persisted columns (three distinct instants).
        assertEquals(CREACION, c.fechaCreacion());
        assertEquals(ACTUALIZACION, c.fechaActualizacion());
        assertEquals(columna("fechaBloqueoAcademico"), c.fechaBloqueoAcademico());
        assertNotNull(c.fechaBloqueoAcademico());
        assertEquals(3, Set.of(c.fechaCreacion(), c.fechaActualizacion(), c.fechaBloqueoAcademico()).size());

        // Case, scenarios, questions and options are the persisted ones, in persisted order.
        assertEquals(caso, c.idCaso());
        assertEquals(UUID.fromString(ids.get("docente")), c.idDocente());
        assertEquals("Publicado", c.estado());
        assertEquals(List.of(escenario), c.escenarios().stream().map(ContextoAcademico.Escenario::idEscenario).toList());
        var preguntas = c.escenarios().getFirst().preguntas();
        assertEquals(List.of(pregunta1, pregunta2), preguntas.stream().map(ContextoAcademico.Pregunta::idPregunta).toList());
        assertEquals(List.of(opcion1a, opcion1b),
                preguntas.getFirst().opciones().stream().map(ContextoAcademico.Opcion::idOpcion).toList());
        assertEquals(sqlIds("SELECT o.\"idOpcion\" FROM \"OpcionRespuesta\" o JOIN \"Pregunta\" p ON p.\"idPregunta\"=o.\"idPregunta\""
                        + " JOIN \"Escenario\" e ON e.\"idEscenario\"=p.\"idEscenario\" WHERE e.\"idCaso\"=?"),
                preguntas.stream().flatMap(p -> p.opciones().stream()).map(ContextoAcademico.Opcion::idOpcion)
                        .collect(Collectors.toSet()));
        // Protected data stays inside the protected snapshot.
        assertTrue(preguntas.getFirst().opciones().getFirst().esCorrecta());
        assertEquals("Explicacion 1A", preguntas.getFirst().opciones().getFirst().explicacion());

        // Rubric and associations correspond to the persisted rubric of this case.
        assertEquals(rubrica, c.rubrica().idRubrica());
        assertEquals(caso, c.rubrica().idCaso());
        assertEquals(0, new BigDecimal("10.00").compareTo(c.rubrica().puntajeTotal()));
        assertEquals(sqlIds("SELECT rp.\"idRubricaPregunta\" FROM \"RubricaPregunta\" rp JOIN \"Rubrica\" r"
                        + " ON r.\"idRubrica\"=rp.\"idRubrica\" WHERE r.\"idCaso\"=?"),
                c.rubrica().preguntas().stream().map(ContextoAcademico.RubricaPregunta::idRubricaPregunta)
                        .collect(Collectors.toSet()));
        var a1 = c.rubrica().preguntas().stream().filter(a -> a.idRubricaPregunta().equals(asociacion1)).findFirst().orElseThrow();
        assertEquals(pregunta1, a1.idPregunta());
        assertEquals(0, new BigDecimal("6.00").compareTo(a1.valorPregunta()));

        assertEquals(antes, huella(), "reading the academic context writes nothing");
        assertEquals(0L, calificaciones());
    }

    // ------------------------------------------------------------------ denial, absence, failure

    @Test
    void capacidadAjenaORevocadaSeDeniegaSinLeerLaBase() {
        var observado = spy(lectorJpa);
        var lector = lectorM3(observado);
        assertThrows(AccesoEvaluacionDenegadoException.class,
                () -> servicio(intentoFinalizado(caso), lector, capacidadAjena).preparar(idIntento));
        // Valid once, then revoked: the next preparation is denied.
        servicio(intentoFinalizado(caso), lector).preparar(idIntento);
        revocadas.add(capacidadValida);
        var denegado = assertThrows(AccesoEvaluacionDenegadoException.class,
                () -> servicio(intentoFinalizado(caso), lector).preparar(idIntento));
        assertSinCapacidad(denegado);
        verify(observado, times(1)).findById(caso);
        assertEquals(0L, calificaciones());
    }

    @Test
    void verificadorNoDisponibleOFalloDelVerificadorSonDenegacionFailClosed() {
        var observado = spy(lectorJpa);
        AutorizacionContextoAcademicoPort noDisponible = (capacidad, idCaso, operacion) -> Decision.NO_DISPONIBLE;
        AutorizacionContextoAcademicoPort roto = (capacidad, idCaso, operacion) -> {
            throw new IllegalStateException("verificador caido con " + capacidad);
        };
        for (var verificador : List.of(noDisponible, roto)) {
            var lector = new ContextoAcademicoProtectedReader(verificador, observado);
            var error = assertThrows(AccesoEvaluacionDenegadoException.class,
                    () -> servicio(intentoFinalizado(caso), consumidor(lector)).preparar(idIntento));
            assertSinCapacidad(error);
        }
        // No verifier at all: M3 denies as well.
        assertThrows(AccesoEvaluacionDenegadoException.class, () -> servicio(intentoFinalizado(caso),
                consumidor(new ContextoAcademicoProtectedReader(null, observado))).preparar(idIntento));
        verifyNoInteractions(observado);
    }

    @Test
    void ausenciaDenegacionYFalloTecnicoSonResultadosDistintos(CapturedOutput salida) {
        // Absence: authorized, but the case does not exist.
        UUID inexistente = UUID.randomUUID();
        var ausencia = assertThrows(PreparacionContextoException.class,
                () -> servicio(intentoFinalizado(inexistente), lectorM3(lectorJpa)).preparar(idIntento));
        assertEquals(Codigo.CONTEXTO_INEXISTENTE, ausencia.codigo());
        // Denial.
        assertThrows(AccesoEvaluacionDenegadoException.class,
                () -> servicio(intentoFinalizado(caso), lectorM3(lectorJpa), capacidadAjena).preparar(idIntento));
        // Technical failure after successful verification: propagates as a failure, never as absence.
        var roto = spy(lectorJpa);
        doThrow(new DataAccessResourceFailureException("base caida con " + capacidadValida)).when(roto).findById(caso);
        var fallo = assertThrows(PreparacionContextoException.class,
                () -> servicio(intentoFinalizado(caso), lectorM3(roto)).preparar(idIntento));
        assertEquals(Codigo.FALLO_CONSULTA_CONTEXTO, fallo.codigo());
        assertSinCapacidad(fallo);
        // No capability reached the logs during the whole test.
        assertFalse(salida.getAll().contains(capacidadValida), "valid capability leaked to logs");
        assertFalse(salida.getAll().contains(capacidadAjena), "foreign capability leaked to logs");
        assertEquals(0L, calificaciones());
    }

    // ------------------------------------------------------------------ test-only composition

    private final UUID idIntento = UUID.randomUUID();

    /** Controlled verifier: accepts only the fixture capability for this case and operation. */
    private ConsultaContextoAcademicoPort lectorM3(CasoJpaAggregateReader lector) {
        AutorizacionContextoAcademicoPort verificador = (capacidad, idCaso, operacion) -> {
            verificaciones.add(operacion);
            return capacidadValida.equals(capacidad) && !revocadas.contains(capacidad)
                    && Operacion.CONSULTAR_CONTEXTO_EVALUABLE == operacion ? Decision.AUTORIZADO : Decision.DENEGADO;
        };
        return consumidor(new ContextoAcademicoProtectedReader(verificador, lector));
    }

    /** Test-only M6 consumer adapter over the real M3 port: map, translate denial, propagate failures. */
    private static ConsultaContextoAcademicoPort consumidor(ContextoAcademicoConsultaPort m3) {
        return (idCaso, capacidad) -> {
            try {
                return m3.consultar(idCaso, capacidad).map(MapeoContextoAcademicoM3::aContextoAcademico);
            } catch (AccesoContextoAcademicoDenegadoException denegado) {
                throw new AccesoEvaluacionDenegadoException();
            }
        };
    }

    private PrepararContextoEvaluacionService servicio(ConsultaIntentoEvaluacionPort intentos, ConsultaContextoAcademicoPort contextos) {
        return servicio(intentos, contextos, capacidadValida);
    }

    private PrepararContextoEvaluacionService servicio(ConsultaIntentoEvaluacionPort intentos,
            ConsultaContextoAcademicoPort contextos, String capacidadEmitida) {
        SolicitudCapacidadContextoAcademicoPort emisor = (intento, idCaso) -> capacidadEmitida;
        return new PrepararContextoEvaluacionService(intentos, emisor, contextos);
    }

    private ConsultaIntentoEvaluacionPort intentoFinalizado(UUID idCaso) {
        var intento = new IntentoEvaluacion(idIntento, UUID.randomUUID(), idCaso, IntentoEvaluacion.EstadoIntento.FINALIZADO,
                List.of(new RespuestaReferencia(pregunta1, opcion1b), new RespuestaReferencia(pregunta2, opcion2a)));
        return id -> idIntento.equals(id) ? Optional.of(intento) : Optional.empty();
    }

    // ------------------------------------------------------------------ evidence helpers

    private UUID id(String nombre) {
        UUID v = UUID.randomUUID();
        ids.put(nombre, v.toString());
        return v;
    }

    private String sql(String plantilla) {
        ids.computeIfAbsent("docente", k -> UUID.randomUUID().toString());
        ids.computeIfAbsent("sesion", k -> UUID.randomUUID().toString());
        ids.computeIfAbsent("asociacion2", k -> UUID.randomUUID().toString());
        String r = plantilla;
        for (var e : ids.entrySet()) { r = r.replace("%" + e.getKey() + "%", e.getValue()); }
        assertFalse(r.matches("(?s).*%[a-z0-9]+%.*"), "unresolved fixture placeholder");
        return r;
    }

    private Instant columna(String nombre) {
        return jdbc.queryForObject("SELECT \"" + nombre + "\" FROM \"Caso\" WHERE \"idCaso\"=?", OffsetDateTime.class, caso)
                .toInstant();
    }

    private Set<UUID> sqlIds(String consulta) {
        return Set.copyOf(jdbc.queryForList(consulta, UUID.class, caso));
    }

    private List<List<Map<String, Object>>> huella() {
        return List.of(
                jdbc.queryForList("SELECT * FROM \"Caso\" WHERE \"idCaso\"=?", caso),
                jdbc.queryForList("SELECT * FROM \"Escenario\" WHERE \"idCaso\"=? ORDER BY \"orden\"", caso),
                jdbc.queryForList("SELECT p.* FROM \"Pregunta\" p JOIN \"Escenario\" e ON e.\"idEscenario\"=p.\"idEscenario\""
                        + " WHERE e.\"idCaso\"=? ORDER BY p.\"idPregunta\"", caso),
                jdbc.queryForList("SELECT * FROM \"Rubrica\" WHERE \"idCaso\"=?", caso),
                jdbc.queryForList("SELECT rp.* FROM \"RubricaPregunta\" rp JOIN \"Rubrica\" r ON r.\"idRubrica\"=rp.\"idRubrica\""
                        + " WHERE r.\"idCaso\"=? ORDER BY rp.\"idRubricaPregunta\"", caso));
    }

    private long calificaciones() { return jdbc.queryForObject("SELECT count(*) FROM \"Calificacion\"", Long.class); }

    private void assertSinCapacidad(RuntimeException error) {
        for (Throwable t = error; t != null; t = t.getCause()) {
            assertFalse(String.valueOf(t.getMessage()).contains(capacidadValida), "capability in error chain");
            assertFalse(String.valueOf(t.getMessage()).contains(capacidadAjena), "capability in error chain");
        }
        assertFalse(error.toString().contains("cap-"), error.toString());
    }
}
