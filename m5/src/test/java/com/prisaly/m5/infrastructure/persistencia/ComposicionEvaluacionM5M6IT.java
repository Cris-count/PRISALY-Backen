package com.prisaly.m5.infrastructure.persistencia;

import static org.junit.jupiter.api.Assertions.*;

import com.prisaly.m4.api.ContextoProgramacion;
import com.prisaly.m4.api.ProgramacionParticipacionPort;
import com.prisaly.m4.infrastructure.adapter.ProgramacionParticipacionJdbcAdapter;
import com.prisaly.m5.application.acceso.*;
import com.prisaly.m5.application.port.RepositorioIntentos;
import com.prisaly.m5.domain.intento.MotivoFinalizacion;
import com.prisaly.m5.infrastructure.FachadaParticipacionTransaccional;
import com.prisaly.m5.infrastructure.evaluacion.FabricaConsultaIntentoEvaluacion;
import com.prisaly.m5.infrastructure.programacion.TitularidadIntentosDesdeM4;
import com.prisaly.m6.application.AccesoEvaluacionDenegadoException;
import com.prisaly.m6.application.PreparacionContextoException;
import com.prisaly.m6.application.PreparacionContextoException.Codigo;
import com.prisaly.m6.application.integration.ContextoAcademico;
import com.prisaly.m6.application.integration.IntentoEvaluacion;
import com.prisaly.m6.application.port.AutorizacionEvaluacionPort;
import com.prisaly.m6.application.port.AutorizacionEvaluacionPort.DecisionAcceso;
import com.prisaly.m6.application.port.ConsultaCalificacionPort;
import com.prisaly.m6.application.port.ConsultaContextoAcademicoPort;
import com.prisaly.m6.application.port.SolicitudCapacidadContextoAcademicoPort;
import com.prisaly.m6.application.service.PrepararContextoEvaluacionService;
import com.prisaly.m6.application.service.PrepararEvaluacionProtegida;
import com.prisaly.m6.application.EvaluacionNoDisponibleException;
import com.prisaly.m6.application.proxy.EvaluacionProtectionProxy;
import com.prisaly.m6.application.service.EvaluarIntentoService;
import com.prisaly.m6.domain.strategy.EntradaEvaluacion;
import com.prisaly.m6.domain.strategy.EstrategiaEvaluacion;
import com.prisaly.m6.domain.strategy.ResultadoEvaluacion;
import com.prisaly.m6.infrastructure.persistence.jdbc.CalificacionJdbcWriter;
import com.prisaly.m6.domain.model.Calificacion;
import com.prisaly.m6.api.SolicitudEvaluacion;
import java.math.BigDecimal;
import java.sql.Connection;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceUtils;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

/**
 * Composition of the real M6 preparation services with the real M5 reader of PR #48, the real
 * M5 protection and the real M4 programming adapter over PostgreSQL 16.15 and V001-V009.
 *
 * <p>Only the providers that do not exist in production yet are test doubles: institutional
 * authorization, capability issuer, the M3 protected context (read here from the same database)
 * and the grade lookup (a plain SQL count). None is registered as a Spring bean. Nothing in this
 * class calculates or publishes a grade, and DD-02/DD-03/DD-04/D07 stay disabled. Only
 * {@code intentoFinalizadoSeEvaluaConEstrategiaExplicitaYSeRegistraUnaSolaVez} (DC-BE-24H-01) persists a grade, with an
 * explicitly supplied TEST strategy and fixture value: it proves the evaluator wiring, not a formula.
 */
@SpringBootTest(classes = RepositorioIntentosJpaIT.AppPrueba.class)
@Testcontainers
class ComposicionEvaluacionM5M6IT {
    @Container static PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:16.15");

    @DynamicPropertySource static void propiedades(DynamicPropertyRegistry r) {
        r.add("spring.datasource.url", postgres::getJdbcUrl);
        r.add("spring.datasource.username", postgres::getUsername);
        r.add("spring.datasource.password", postgres::getPassword);
        r.add("spring.jpa.hibernate.ddl-auto", () -> "validate");
        r.add("spring.jpa.properties.hibernate.globally_quoted_identifiers", () -> "true");
        r.add("spring.flyway.locations", RepositorioIntentosJpaIT::ubicacionFlywayCanonica);
    }

    private static final String CAPACIDAD = "capacidad-de-prueba";

    @Autowired JdbcTemplate jdbc;
    @Autowired RepositorioIntentos repositorio;
    @Autowired PlatformTransactionManager tx;
    @Autowired FachadaParticipacionTransaccional fachada;

    CadenaIntentoPrueba cadena;
    final AtomicInteger capacidadesSolicitadas = new AtomicInteger();
    final AtomicInteger contextosLeidos = new AtomicInteger();
    final AtomicInteger calificacionesConsultadas = new AtomicInteger();

    @BeforeEach void crear() { cadena = CadenaIntentoPrueba.crear(jdbc); }

    // ---------------------------------------------------------------- composition

    @Test void composicionCompletaAutorizaEnCadaCapaYNoEscribeNada() {
        finalizarConRespuesta();
        var solicitud = new SolicitudEvaluacion(cadena.idIntento(), UUID.randomUUID());
        var decisiones = new ArrayList<SolicitudEvaluacion>();
        AutorizacionEvaluacionPort autorizador = s -> { decisiones.add(s); return DecisionAcceso.AUTORIZADO; };
        var antes = huella();

        var servicio = servicio(autorizador, lector(autorizador, solicitud, cadena.estudiante(), programacionReal()),
                capacidades(), contextoDesdeBd(solicitud.idRubrica()));
        var primero = servicio.preparar(solicitud);
        var segundo = servicio.preparar(solicitud);

        IntentoEvaluacion intento = primero.intento();
        assertEquals(cadena.idIntento(), intento.idIntento());
        assertEquals(jdbc.queryForObject("SELECT \"idParticipacion\" FROM \"Intento\" WHERE \"idIntento\"=?",
                UUID.class, cadena.idIntento()), intento.idParticipacion());
        assertEquals(cadena.caso(), intento.idCaso());
        assertEquals(cadena.caso(), primero.contextoAcademico().idCaso());
        assertEquals(solicitud.idRubrica(), primero.contextoAcademico().rubrica().idRubrica());
        // The immutable M6 model mirrors the persisted state exactly.
        var persistido = jdbc.queryForMap("SELECT \"estado\", \"estadoEvaluacion\" FROM \"Intento\" WHERE \"idIntento\"=?",
                cadena.idIntento());
        assertEquals("Finalizado", persistido.get("estado"));
        assertEquals(IntentoEvaluacion.EstadoIntento.FINALIZADO, intento.estado());
        // Preparing is not evaluating: the persisted evaluation state is untouched.
        assertEquals("Pendiente", persistido.get("estadoEvaluacion"));
        assertEquals(respuestasPersistidas(), intento.respuestas().stream()
                .map(r -> List.of(r.idPregunta(), r.idOpcionSeleccionada())).toList());
        assertThrows(UnsupportedOperationException.class, () -> intento.respuestas().clear());
        assertEquals(primero, segundo);
        // Authorization is evaluated per call in M6 and again inside the M5 reader: 2 calls x 2 layers.
        assertEquals(List.of(solicitud, solicitud, solicitud, solicitud), decisiones);
        assertEquals(2, capacidadesSolicitadas.get());
        assertEquals(2, contextosLeidos.get());
        assertEquals(2, calificacionesConsultadas.get());
        assertEquals(antes, huella(), "preparation must not write any row");
        assertEquals(0, calificaciones());
    }

    @Test void autorizacionRevocadaEntreCapasSeDeniegaNoSeConvierteEnFalloNiAusencia() {
        finalizarConRespuesta();
        var solicitud = new SolicitudEvaluacion(cadena.idIntento(), UUID.randomUUID());
        var llamadas = new AtomicInteger();
        // M6 authorizes; the institutional decision changes before the M5 reader runs.
        AutorizacionEvaluacionPort autorizador = s -> llamadas.incrementAndGet() == 1
                ? DecisionAcceso.AUTORIZADO : DecisionAcceso.NO_DISPONIBLE;
        var servicio = servicio(autorizador, lector(autorizador, solicitud, cadena.estudiante(), programacionReal()),
                capacidades(), contextoDesdeBd(solicitud.idRubrica()));
        assertThrows(AccesoEvaluacionDenegadoException.class, () -> servicio.preparar(solicitud));
        assertEquals(2, llamadas.get());
        assertNoInstitutionalReads();
    }

    @Test void unLectorLigadoAUnaSolicitudNoSirveParaOtroIntento() {
        finalizarConRespuesta();
        var otra = CadenaIntentoPrueba.crear(jdbc);
        var ligada = new SolicitudEvaluacion(cadena.idIntento(), UUID.randomUUID());
        var ajena = new SolicitudEvaluacion(otra.idIntento(), ligada.idRubrica());
        var autorizadas = new ArrayList<SolicitudEvaluacion>();
        AutorizacionEvaluacionPort autorizador = s -> { autorizadas.add(s); return DecisionAcceso.AUTORIZADO; };
        // Misassembled composition: the reader was created for `ligada` but is asked for `ajena`.
        var servicio = servicio(autorizador, lector(autorizador, ligada, otra.estudiante(), programacionReal()),
                capacidades(), contextoDesdeBd(ligada.idRubrica()));
        assertThrows(AccesoEvaluacionDenegadoException.class, () -> servicio.preparar(ajena));
        assertEquals(List.of(ajena), autorizadas, "the M5 reader rejects the UUID before authorizing or reading");
        assertNoInstitutionalReads();
    }

    @Test void intentoInexistenteYEnCursoSeRechazanSinSolicitarCapacidad() {
        var ausente = new SolicitudEvaluacion(UUID.randomUUID(), UUID.randomUUID());
        AutorizacionEvaluacionPort autorizador = s -> DecisionAcceso.AUTORIZADO;
        codigo(Codigo.INTENTO_INEXISTENTE, () -> servicio(autorizador,
                lector(autorizador, ausente, cadena.estudiante(), programacionReal()), capacidades(),
                contextoDesdeBd(ausente.idRubrica())).preparar(ausente));

        fachada.registrarRespuesta(cadena.intento(), cadena.pregunta1(), cadena.opcion1a());
        var enCurso = new SolicitudEvaluacion(cadena.idIntento(), UUID.randomUUID());
        var antes = huella();
        codigo(Codigo.INTENTO_NO_FINALIZADO, () -> servicio(autorizador,
                lector(autorizador, enCurso, cadena.estudiante(), programacionReal()), capacidades(),
                contextoDesdeBd(enCurso.idRubrica())).preparar(enCurso));
        assertEquals(antes, huella());
        assertNoInstitutionalReads();
    }

    @Test void titularidadAjenaEsDenegacionYFalloTecnicoEsFalloNuncaAusencia() {
        finalizarConRespuesta();
        var solicitud = new SolicitudEvaluacion(cadena.idIntento(), UUID.randomUUID());
        AutorizacionEvaluacionPort autorizador = s -> DecisionAcceso.AUTORIZADO;
        // Institutional authorization never replaces the persisted student ownership.
        var ajena = CadenaIntentoPrueba.crear(jdbc);
        assertThrows(AccesoEvaluacionDenegadoException.class, () -> servicio(autorizador,
                lector(autorizador, solicitud, ajena.estudiante(), programacionReal()), capacidades(),
                contextoDesdeBd(solicitud.idRubrica())).preparar(solicitud));
        // A programming lookup failure inside the read is technical and keeps no diagnostics.
        var real = programacionReal();
        ProgramacionParticipacionPort caida = new ProgramacionParticipacionPort() {
            public Optional<ContextoProgramacion> consultarSesion(UUID s, UUID e) { return real.consultarSesion(s, e); }
            public Optional<ContextoProgramacion> consultarConvocatoria(UUID c, UUID e) {
                throw new IllegalStateException("conexion perdida con datos sensibles");
            }
        };
        var error = codigo(Codigo.FALLO_CONSULTA_INTENTO, () -> servicio(autorizador,
                lector(autorizador, solicitud, cadena.estudiante(), caida), capacidades(),
                contextoDesdeBd(solicitud.idRubrica())).preparar(solicitud));
        assertNull(error.getCause());
        assertFalse(error.toString().contains("sensibles"));
        assertNoInstitutionalReads();
    }

    @Test void laLecturaDeM5CorreEnTransaccionDeSoloLecturaQuePostgreSqlHaceCumplir() {
        finalizarConRespuesta();
        var solicitud = new SolicitudEvaluacion(cadena.idIntento(), UUID.randomUUID());
        AutorizacionEvaluacionPort autorizador = s -> DecisionAcceso.AUTORIZADO;
        var real = programacionReal();
        var soloLectura = new AtomicReference<Boolean>();
        var aislamiento = new AtomicReference<Integer>();
        var escritura = new AtomicReference<RuntimeException>();
        ProgramacionParticipacionPort espia = new ProgramacionParticipacionPort() {
            public Optional<ContextoProgramacion> consultarSesion(UUID s, UUID e) { return real.consultarSesion(s, e); }
            public Optional<ContextoProgramacion> consultarConvocatoria(UUID c, UUID e) {
                soloLectura.set(TransactionSynchronizationManager.isCurrentTransactionReadOnly());
                aislamiento.set(TransactionSynchronizationManager.getCurrentTransactionIsolationLevel());
                Connection conexion = DataSourceUtils.getConnection(jdbc.getDataSource());
                try {
                    soloLectura.set(soloLectura.get() && conexion.isReadOnly());
                } catch (java.sql.SQLException sql) {
                    throw new IllegalStateException(sql);
                }
                try {
                    // Any accidental write in the reader is rejected by PostgreSQL itself.
                    jdbc.update("UPDATE \"Intento\" SET \"limiteTemporal\" = \"limiteTemporal\" + interval '1 hour'"
                            + " WHERE \"idIntento\" = ?", cadena.idIntento());
                } catch (RuntimeException rechazo) {
                    escritura.set(rechazo);
                    throw rechazo;
                }
                return real.consultarConvocatoria(c, e);
            }
        };
        var antes = huella();
        codigo(Codigo.FALLO_CONSULTA_INTENTO, () -> servicio(autorizador,
                lector(autorizador, solicitud, cadena.estudiante(), espia), capacidades(),
                contextoDesdeBd(solicitud.idRubrica())).preparar(solicitud));
        assertTrue(soloLectura.get(), "transaction and JDBC connection must be read-only");
        assertEquals(Connection.TRANSACTION_REPEATABLE_READ, aislamiento.get());
        assertNotNull(escritura.get(), "PostgreSQL must reject the write");
        assertTrue(String.valueOf(escritura.get().getMessage()).contains("read-only"), escritura.get().getMessage());
        assertEquals(antes, huella());
    }

    @Test void respuestaPersistidaFueraDelContextoAcademicoEsInconsistencia() {
        finalizarConRespuesta();
        var solicitud = new SolicitudEvaluacion(cadena.idIntento(), UUID.randomUUID());
        AutorizacionEvaluacionPort autorizador = s -> DecisionAcceso.AUTORIZADO;
        UUID respondida = UUID.fromString(cadena.opcion1a().valor());
        // The M3 snapshot no longer contains the option the student actually persisted.
        ConsultaContextoAcademicoPort sinLaOpcion = (caso, capacidad) -> contextoDesdeBd(solicitud.idRubrica())
                .findByCasoId(caso, capacidad).map(c -> sinOpcion(c, respondida));
        codigo(Codigo.REFERENCIAS_INCONSISTENTES, () -> servicio(autorizador,
                lector(autorizador, solicitud, cadena.estudiante(), programacionReal()), capacidades(),
                sinLaOpcion).preparar(solicitud));
        assertEquals(0, calificaciones());
    }

    // DC-BE-24H-01: finalized attempt -> real M6 preparation -> Proxy -> evaluator with an explicit TEST strategy ->
    // real insert-only writer. Production supplies no strategy, so the official evaluation stays blocked.
    @Test void intentoFinalizadoSeEvaluaConEstrategiaExplicitaYSeRegistraUnaSolaVez() {
        cadena = CadenaIntentoPrueba.crearConRubrica(jdbc); // the rubric must exist before the session is programmed
        finalizarConRespuesta();
        var solicitud = new SolicitudEvaluacion(cadena.idIntento(), cadena.rubrica());
        AutorizacionEvaluacionPort autorizador = s -> DecisionAcceso.AUTORIZADO;
        var preparacion = new PrepararEvaluacionProtegida(autorizador, notasDesdeBd(), new PrepararContextoEvaluacionService(
                lector(autorizador, solicitud, cadena.estudiante(), programacionReal()), capacidades(),
                contextoDesdeBd(solicitud.idRubrica())));
        var vistas = new ArrayList<EntradaEvaluacion>();
        EstrategiaEvaluacion dePrueba = s -> { vistas.add(s); return new ResultadoEvaluacion(new BigDecimal("1.00"), "{\"fixture\":true}"); };
        var writer = new CalificacionJdbcWriter(jdbc);

        var bloqueado = new EvaluarIntentoService(preparacion, Map.of("estrategia-prueba", dePrueba), "", writer);
        assertThrows(EvaluacionNoDisponibleException.class, () -> new EvaluacionProtectionProxy(autorizador, bloqueado).evaluar(solicitud));
        assertEquals(0, calificaciones());
        assertEquals(0, capacidadesSolicitadas.get(), "a blocked evaluation reads nothing");

        var proxy = new EvaluacionProtectionProxy(autorizador,
                new EvaluarIntentoService(preparacion, Map.of("estrategia-prueba", dePrueba), "estrategia-prueba", writer));
        assertEquals(new BigDecimal("1.00"), proxy.evaluar(solicitud).valor());
        var fila = jdbc.queryForMap("SELECT \"idRubrica\", \"valor\", \"versionPolitica\" FROM \"Calificacion\" WHERE \"idIntento\"=?",
                cadena.idIntento());
        assertEquals(solicitud.idRubrica(), fila.get("idRubrica"));
        assertEquals(new BigDecimal("1.00"), fila.get("valor"));
        assertEquals("estrategia-prueba", fila.get("versionPolitica"));
        // C2: the strategy received the prepared context built from persisted rows, uninterpreted.
        assertEquals(1, vistas.size());
        EntradaEvaluacion entrada = vistas.getFirst();
        assertEquals(solicitud, entrada.solicitud());
        assertEquals(List.of(UUID.fromString(cadena.pregunta1().valor()), UUID.fromString(cadena.pregunta2().valor())),
                entrada.preguntas().stream().map(EntradaEvaluacion.PreguntaEvaluable::idPregunta).toList());
        assertEquals(respuestasPersistidas().getFirst().get(1), entrada.preguntas().get(0).opcionSeleccionada().orElseThrow());
        assertEquals(UUID.fromString(cadena.opcion1a().valor()), entrada.preguntas().get(0).opcionSeleccionada().orElseThrow());
        assertEquals(Optional.empty(), entrada.preguntas().get(1).opcionSeleccionada());
        assertEquals(jdbc.queryForObject("SELECT \"valorPregunta\" FROM \"RubricaPregunta\" WHERE \"idRubrica\"=?",
                BigDecimal.class, cadena.rubrica()), entrada.preguntas().get(0).valorPregunta().orElseThrow());
        assertEquals(new BigDecimal("2.50"), entrada.preguntas().get(0).valorPregunta().orElseThrow());
        assertEquals(Optional.empty(), entrada.preguntas().get(1).valorPregunta());
        assertEquals(Optional.of(jdbc.queryForObject("SELECT \"definicionEvaluacion\"::text FROM \"Rubrica\" WHERE \"idRubrica\"=?",
                String.class, cadena.rubrica())), entrada.definicionEvaluacionRubrica());
        // The evaluation state catalogue (PD-11) is pending: it is not changed.
        assertEquals("Pendiente", jdbc.queryForObject("SELECT \"estadoEvaluacion\" FROM \"Intento\" WHERE \"idIntento\"=?",
                String.class, cadena.idIntento()));

        // Re-evaluation is refused by the preparation before the strategy runs; the grade is not overwritten.
        assertThrows(IllegalStateException.class, () -> proxy.evaluar(solicitud));
        assertEquals(1, vistas.size());
        assertEquals(1, calificaciones());
    }

    private ConsultaCalificacionPort notasDesdeBd() {
        return new ConsultaCalificacionPort() {
            public Optional<Calificacion> findById(UUID id) { throw new UnsupportedOperationException(); }
            public Optional<Calificacion> findByIntentoId(UUID id) {
                return jdbc.query("SELECT \"idCalificacion\", \"idIntento\", \"idRubrica\", \"valor\", \"fechaCalculo\","
                                + " \"detalleEvaluacion\"::text, \"versionPolitica\" FROM \"Calificacion\" WHERE \"idIntento\"=?",
                        (rs, n) -> new Calificacion(rs.getObject(1, UUID.class), rs.getObject(2, UUID.class),
                                rs.getObject(3, UUID.class), rs.getBigDecimal(4), rs.getTimestamp(5).toInstant(),
                                rs.getString(6), rs.getString(7)), id).stream().findFirst();
            }
        };
    }

    // ---------------------------------------------------------------- assembly helpers

    private PrepararEvaluacionProtegida servicio(AutorizacionEvaluacionPort autorizador,
            com.prisaly.m6.application.port.ConsultaIntentoEvaluacionPort lector,
            SolicitudCapacidadContextoAcademicoPort capacidades, ConsultaContextoAcademicoPort contextos) {
        ConsultaCalificacionPort notas = new ConsultaCalificacionPort() {
            public Optional<Calificacion> findById(UUID id) { throw new UnsupportedOperationException(); }
            public Optional<Calificacion> findByIntentoId(UUID id) {
                calificacionesConsultadas.incrementAndGet();
                if (jdbc.queryForObject("SELECT count(*) FROM \"Calificacion\" WHERE \"idIntento\"=?", Long.class, id) > 0) {
                    throw new IllegalStateException("unexpected grade in fixture");
                }
                return Optional.empty();
            }
        };
        return new PrepararEvaluacionProtegida(autorizador, notas,
                new PrepararContextoEvaluacionService(lector, capacidades, contextos));
    }

    private com.prisaly.m6.application.port.ConsultaIntentoEvaluacionPort lector(
            AutorizacionEvaluacionPort autorizador, SolicitudEvaluacion solicitud, UUID estudiante,
            ProgramacionParticipacionPort programacion) {
        IdentidadEstudianteActual identidad = () -> Optional.of(new IdentificadorEstudiante(estudiante.toString()));
        var protegida = new AccesoProtegidoParticipacion(new ControlAccesoIntentos(identidad,
                new TitularidadIntentosDesdeM4(jdbc, programacionReal())), fachada);
        return new FabricaConsultaIntentoEvaluacion(autorizador, protegida, repositorio, identidad, programacion, tx)
                .para(solicitud);
    }

    private ProgramacionParticipacionPort programacionReal() { return new ProgramacionParticipacionJdbcAdapter(jdbc); }

    private SolicitudCapacidadContextoAcademicoPort capacidades() {
        return (intento, caso) -> { capacidadesSolicitadas.incrementAndGet(); return CAPACIDAD; };
    }

    /** Stand-in for M3's protected producer: reads the real case rows; the rubric is an in-memory snapshot. */
    private ConsultaContextoAcademicoPort contextoDesdeBd(UUID idRubrica) {
        UUID idAsociacion = UUID.randomUUID(); // stable across reads, like a persisted row
        return (idCaso, capacidad) -> {
            assertEquals(CAPACIDAD, capacidad);
            contextosLeidos.incrementAndGet();
            var caso = jdbc.queryForMap("SELECT * FROM \"Caso\" WHERE \"idCaso\"=?", idCaso);
            var escenarios = jdbc.queryForList("SELECT * FROM \"Escenario\" WHERE \"idCaso\"=? ORDER BY \"orden\"", idCaso)
                    .stream().map(e -> new ContextoAcademico.Escenario((UUID) e.get("idEscenario"), idCaso,
                            (String) e.get("titulo"), (String) e.get("contexto"), (String) e.get("instrucciones"),
                            (Integer) e.get("orden"), (Boolean) e.get("activo"), preguntas((UUID) e.get("idEscenario"))))
                    .toList();
            UUID pregunta = escenarios.getFirst().preguntas().getFirst().idPregunta();
            var persistida = jdbc.queryForList("SELECT \"nombre\", \"definicionEvaluacion\"::text AS definicion"
                    + " FROM \"Rubrica\" WHERE \"idRubrica\"=?", idRubrica);
            // A persisted rubric (C2 fixture) is read with its real associations; otherwise the in-memory snapshot.
            var rubrica = persistida.isEmpty()
                    ? new ContextoAcademico.Rubrica(idRubrica, idCaso, "Rubrica de prueba", null, null, null, null,
                            Set.of(new ContextoAcademico.RubricaPregunta(idAsociacion, idRubrica, pregunta, BigDecimal.ONE, null)))
                    : new ContextoAcademico.Rubrica(idRubrica, idCaso, (String) persistida.getFirst().get("nombre"), null,
                            null, (String) persistida.getFirst().get("definicion"), null, Set.copyOf(jdbc.query(
                                    "SELECT \"idRubricaPregunta\", \"idPregunta\", \"valorPregunta\", \"criterio\""
                                            + " FROM \"RubricaPregunta\" WHERE \"idRubrica\"=?",
                                    (rs, n) -> new ContextoAcademico.RubricaPregunta(rs.getObject(1, UUID.class), idRubrica,
                                            rs.getObject(2, UUID.class), rs.getBigDecimal(3), rs.getString(4)),
                                    idRubrica)));
            return Optional.of(new ContextoAcademico(idCaso, (UUID) caso.get("idDocente"), (String) caso.get("nombre"),
                    (String) caso.get("historia"), (String) caso.get("desarrolloSituacional"),
                    (Integer) caso.get("tiempoEstimadoSegundos"), (String) caso.get("estado"), (Boolean) caso.get("activo"),
                    null, null, null, escenarios, rubrica));
        };
    }

    private List<ContextoAcademico.Pregunta> preguntas(UUID escenario) {
        return jdbc.queryForList("SELECT * FROM \"Pregunta\" WHERE \"idEscenario\"=? ORDER BY \"orden\"", escenario)
                .stream().map(p -> new ContextoAcademico.Pregunta((UUID) p.get("idPregunta"), escenario,
                        (String) p.get("enunciado"), (String) p.get("textoAyuda"), (String) p.get("estadoEdicion"),
                        (Boolean) p.get("visible"), (Integer) p.get("orden"), null, null,
                        jdbc.queryForList("SELECT * FROM \"OpcionRespuesta\" WHERE \"idPregunta\"=? ORDER BY \"orden\"",
                                p.get("idPregunta")).stream().map(o -> new ContextoAcademico.Opcion(
                                        (UUID) o.get("idOpcion"), (UUID) p.get("idPregunta"), (String) o.get("texto"),
                                        (Boolean) o.get("esCorrecta"), (String) o.get("explicacion"),
                                        (Integer) o.get("orden"))).toList()))
                .toList();
    }

    private static ContextoAcademico sinOpcion(ContextoAcademico c, UUID opcion) {
        var escenarios = c.escenarios().stream().map(e -> new ContextoAcademico.Escenario(e.idEscenario(), e.idCaso(),
                e.titulo(), e.contexto(), e.instrucciones(), e.orden(), e.activo(), e.preguntas().stream()
                        .map(p -> new ContextoAcademico.Pregunta(p.idPregunta(), p.idEscenario(), p.enunciado(),
                                p.textoAyuda(), p.estadoEdicion(), p.visible(), p.orden(), p.fechaCreacion(),
                                p.fechaActualizacion(), p.opciones().stream()
                                        .filter(o -> !o.idOpcion().equals(opcion)).toList()))
                        .toList())).toList();
        return new ContextoAcademico(c.idCaso(), c.idDocente(), c.nombre(), c.historia(), c.desarrolloSituacional(),
                c.tiempoEstimadoSegundos(), c.estado(), c.activo(), c.fechaCreacion(), c.fechaActualizacion(),
                c.fechaBloqueoAcademico(), escenarios, c.rubrica());
    }

    // ---------------------------------------------------------------- database evidence

    private void finalizarConRespuesta() {
        fachada.registrarRespuesta(cadena.intento(), cadena.pregunta1(), cadena.opcion1a());
        fachada.finalizarIntento(cadena.intento(), MotivoFinalizacion.ENTREGA_ESTUDIANTE);
    }

    private List<List<UUID>> respuestasPersistidas() {
        return jdbc.query("SELECT \"idPregunta\", \"idOpcionSeleccionada\" FROM \"Respuesta\" WHERE \"idIntento\"=?"
                        + " ORDER BY \"idPregunta\"",
                (rs, n) -> List.of(rs.getObject(1, UUID.class), rs.getObject(2, UUID.class)), cadena.idIntento());
    }

    /** Full row content of every table the flow can touch, for this chain. */
    private List<List<Map<String, Object>>> huella() {
        return List.of(
                jdbc.queryForList("SELECT * FROM \"Intento\" WHERE \"idIntento\"=?", cadena.idIntento()),
                jdbc.queryForList("SELECT * FROM \"Respuesta\" WHERE \"idIntento\"=? ORDER BY \"idPregunta\"", cadena.idIntento()),
                jdbc.queryForList("SELECT * FROM \"Participacion\" WHERE \"idConvocatoria\"=?", cadena.convocatoria()),
                jdbc.queryForList("SELECT * FROM \"Convocatoria\" WHERE \"idConvocatoria\"=?", cadena.convocatoria()),
                jdbc.queryForList("SELECT * FROM \"Caso\" WHERE \"idCaso\"=?", cadena.caso()),
                jdbc.queryForList("SELECT count(*) AS n FROM \"Calificacion\""),
                jdbc.queryForList("SELECT count(*) AS n FROM \"RetroalimentacionGeneral\""));
    }

    private long calificaciones() { return jdbc.queryForObject("SELECT count(*) FROM \"Calificacion\"", Long.class); }

    private void assertNoInstitutionalReads() {
        assertEquals(0, capacidadesSolicitadas.get(), "no capability may be requested");
        assertEquals(0, contextosLeidos.get(), "no academic context may be read");
    }

    private static PreparacionContextoException codigo(Codigo esperado, Runnable accion) {
        var error = assertThrows(PreparacionContextoException.class, accion::run);
        assertEquals(esperado, error.codigo());
        return error;
    }
}
