package com.prisaly.m5.infrastructure;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.prisaly.PrisalyApplication;
import com.prisaly.m3.api.ContenidoCasoEstudiantilPort;
import com.prisaly.m3.api.GuionNarrativoEstudiantilPort;
import com.prisaly.m4.api.ProgramacionInicioCoordinadoPort;
import com.prisaly.m4.api.ProgramacionParticipacionPort;
import com.prisaly.m4.infrastructure.config.M4SchemaAutoConfiguration;
import com.prisaly.m4.infrastructure.config.M4SchemaContract;
import com.prisaly.m5.application.LimiteTemporalVencidoException;
import com.prisaly.m5.application.ReferenciaConvocatoria;
import com.prisaly.m5.application.ReferenciaIntento;
import com.prisaly.m5.application.ResultadoInicio;
import com.prisaly.m5.application.acceso.AccesoIntentoDenegadoException;
import com.prisaly.m5.application.acceso.AccesoProtegidoParticipacion;
import com.prisaly.m5.application.acceso.EstudianteNoIdentificadoException;
import com.prisaly.m5.application.acceso.IdentidadEstudianteActual;
import com.prisaly.m5.application.acceso.IdentificadorEstudiante;
import com.prisaly.m5.application.acceso.TitularidadIntentos;
import com.prisaly.m5.application.fase.CompletitudIntento;
import com.prisaly.m5.application.fase.CuestionarioNoIniciadoException;
import com.prisaly.m5.application.fase.EntradaCuestionario;
import com.prisaly.m5.application.fase.EstadoFase;
import com.prisaly.m5.application.fase.ExploracionCerradaException;
import com.prisaly.m5.application.fase.FaseAcademica;
import com.prisaly.m5.application.fase.FasesIntento;
import com.prisaly.m5.application.fase.NarrativaIntento;
import com.prisaly.m5.application.fase.NarrativaNoDisponibleException;
import com.prisaly.m5.application.fase.PoliticasFaseAprobadas;
import com.prisaly.m5.application.fase.SinFasesException;
import com.prisaly.m5.application.inicio.ElegibilidadInicio;
import com.prisaly.m5.application.port.GuionesDelIntento;
import com.prisaly.m5.application.port.RepositorioFases;
import com.prisaly.m5.application.port.RepositorioIntentos;
import com.prisaly.m5.application.port.RepositorioParticipaciones;
import com.prisaly.m5.domain.intento.EstadoIntento;
import com.prisaly.m5.domain.intento.IntentoFinalizadoException;
import com.prisaly.m5.domain.intento.MotivoFinalizacion;
import com.prisaly.m5.domain.respuesta.ReferenciaPregunta;
import com.prisaly.m5.domain.respuesta.ValorRespuesta;
import com.prisaly.m5.infrastructure.contenido.ConsultaContenidoIntentoTransaccional;
import com.prisaly.m5.infrastructure.persistencia.RepositorioFasesJdbc;
import com.prisaly.m5.infrastructure.programacion.CoordinacionInicioDesdeM4;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Proxy;
import java.sql.SQLException;
import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.postgresql.PostgreSQLContainer;

/**
 * DC-BE-SIM-B1-B2A-24 (Incremento B, B1 y B2a) sobre el APP real y PostgreSQL 16.15 con V001-V009, V013 y las
 * candidatas V017 ({@code db/sim-propuesta}) y V018 ({@code db/sim-propuesta-b}), contrato {@code bd0052-v013-sim-ab}.
 *
 * <p><b>Solo QA.</b> En ESTE contexto se activan el guion de M3 y las fases de M5; la configuración entregada las deja
 * desactivadas. Las políticas de inicio de esta prueba son dobles explícitos (elegible siempre, límite fijo), no reglas
 * académicas; la identidad es un doble porque DD-02 sigue desactivado. Todo lo demás es el código productivo: Proxy,
 * titularidad real de M4, coordinación de inicio, contenido de M3 y el ensamblado de {@link ConfiguracionParticipacion}.
 * Los guiones son datos de prueba, no contenido aprobado.
 */
@SpringBootTest(classes = PrisalyApplication.class)
@DisplayName("Fases académicas del intento (Incremento B, B1-B2a-B2b) en PostgreSQL 16.15")
class FasesIntentoPostgreSqlIT {

    static final PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:16.15");
    static { postgres.start(); }

    @AfterAll static void detener() { postgres.stop(); }

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry r) {
        r.add("spring.datasource.url", postgres::getJdbcUrl);
        r.add("spring.datasource.username", postgres::getUsername);
        r.add("spring.datasource.password", postgres::getPassword);
        r.add("spring.datasource.hikari.maximum-pool-size", () -> "16");
        r.add("spring.flyway.locations", () -> "classpath:db/migration,classpath:db/sim-propuesta,classpath:db/sim-propuesta-b");
        r.add("prisaly.m4.schema-contract", () -> M4SchemaContract.V013_SIM_AB);
        r.add("prisaly.m3.narrativa.publicacion.enabled", () -> "true");
        r.add("prisaly.m5.exploracion.fases.enabled", () -> "true");
    }

    static final String GUION = """
            {"formato":"prisaly-guion-1","inicio":"entrada",
             "espacios":[{"idEspacio":"entrada","titulo":"Entrada (prueba)"}],
             "personajes":[{"idPersonaje":"guia","nombre":"Guía (prueba)"}],
             "intervenciones":[]}""";

    @Autowired JdbcTemplate jdbc;
    @Autowired RepositorioIntentos repositorio;
    @Autowired RepositorioParticipaciones participaciones;
    @Autowired PlatformTransactionManager transacciones;
    @Autowired TitularidadIntentos titularidad;
    @Autowired ProgramacionInicioCoordinadoPort coordinacion;
    @Autowired ProgramacionParticipacionPort programacion;
    @Autowired ContenidoCasoEstudiantilPort contenido;
    @Autowired GuionNarrativoEstudiantilPort guiones;
    @Autowired com.prisaly.m3.infrastructure.persistence.jdbc.GuionNarrativoJdbcAdapter guionesM3;
    /** El bean productivo de M5, ensamblado con las fases activas en este contexto. */
    @Autowired AccesoProtegidoParticipacion produccion;

    final AtomicReference<Optional<IdentificadorEstudiante>> actual = new AtomicReference<>(Optional.empty());
    final IdentidadEstudianteActual identidad = () -> actual.get();

    record Cadena(UUID estudiante, UUID caso, UUID docente, UUID convocatoria, UUID p1, UUID p2, UUID o1, UUID o2,
            Optional<UUID> guion) {
        ReferenciaConvocatoria referencia() { return new ReferenciaConvocatoria(convocatoria.toString()); }
    }

    @BeforeEach void sinIdentidad() { actual.set(Optional.empty()); }

    // --- Fixture (datos de prueba) ------------------------------------------------------------------------------

    UUID usuario(String prefijo) {
        String s = UUID.randomUUID().toString().substring(0, 8);
        Integer rol = jdbc.queryForObject("INSERT INTO \"Rol\" (\"nombre\",\"descripcion\") VALUES (?, 'Fixture B') RETURNING \"idRol\"",
                Integer.class, prefijo + "-" + s);
        UUID id = UUID.randomUUID();
        jdbc.update("INSERT INTO \"Usuario\" (\"idUsuario\",\"idRol\",\"nombre\",\"correo\") VALUES (?,?,?,?)",
                id, rol, prefijo + " fixture", prefijo.toLowerCase() + "." + s + "@prisaly.test");
        return id;
    }

    /** Through the real M3 adapter of Increment A, so the stored fingerprint is the genuine one that M3 verifies. */
    UUID guionPublicado(UUID caso, UUID docente, String estado) {
        return new TransactionTemplate(transacciones).execute(tx -> {
            var borrador = guionesM3.guardarBorrador(caso, docente, GUION);
            return "Borrador".equals(estado) ? borrador.idGuion() : guionesM3.publicarBorrador(caso).idGuion();
        });
    }

    /** Caso publicado (1 escenario, 2 preguntas), guion opcional publicado ANTES de programar, sesión y convocatoria. */
    Cadena cadena(boolean conGuion) {
        UUID docente = usuario("DocB");
        UUID estudiante = UUID.randomUUID();
        jdbc.update("INSERT INTO \"Estudiante\" (\"idEstudiante\",\"idUsuario\") VALUES (?,?)", estudiante, usuario("EstB"));
        UUID caso = UUID.randomUUID(), escenario = UUID.randomUUID(), p1 = UUID.randomUUID(), p2 = UUID.randomUUID(),
                o1 = UUID.randomUUID(), o2 = UUID.randomUUID();
        jdbc.update("INSERT INTO \"Caso\" (\"idCaso\",\"idDocente\",\"nombre\",\"historia\",\"tiempoEstimadoSegundos\",\"estado\") VALUES (?,?,?,'Historia de prueba',600,'Publicado')",
                caso, docente, "Caso B " + caso.toString().substring(0, 8));
        jdbc.update("INSERT INTO \"Escenario\" (\"idEscenario\",\"idCaso\",\"titulo\",\"orden\") VALUES (?,?,'Escenario B',1)", escenario, caso);
        jdbc.update("INSERT INTO \"Pregunta\" (\"idPregunta\",\"idEscenario\",\"enunciado\",\"estadoEdicion\",\"orden\") VALUES (?,?,'P1','Lista',1),(?,?,'P2','Lista',2)",
                p1, escenario, p2, escenario);
        jdbc.update("INSERT INTO \"OpcionRespuesta\" (\"idOpcion\",\"idPregunta\",\"texto\",\"orden\") VALUES (?,?,'1A',1),(?,?,'2A',1)",
                o1, p1, o2, p2);
        Optional<UUID> guion = conGuion ? Optional.of(guionPublicado(caso, docente, "Publicado")) : Optional.empty();
        UUID sesion = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO "Sesion" ("idSesion","idCaso","idDocenteProgramador","nombreEspacioAcademico",
                  "inicioPrevisto","finPrevisto","tiempoMaximoParticipacionSegundos")
                VALUES (?,?,?,'Espacio B',now()-interval '1 hour',now()+interval '1 hour',3600)""", sesion, caso, docente);
        UUID convocatoria = UUID.randomUUID();
        jdbc.update("INSERT INTO \"Convocatoria\" (\"idConvocatoria\",\"idSesion\",\"idEstudiante\") VALUES (?,?,?)",
                convocatoria, sesion, estudiante);
        return new Cadena(estudiante, caso, docente, convocatoria, p1, p2, o1, o2, guion);
    }

    void como(Cadena c) { actual.set(Optional.of(new IdentificadorEstudiante(c.estudiante().toString()))); }

    /** Ensamblado real de M5 con fases candidatas y políticas de inicio de prueba (límite fijo). */
    AccesoProtegidoParticipacion acceso(Duration limite) {
        return acceso(limite, ConfiguracionParticipacion.fases(true, identidad, programacion, guiones, jdbc));
    }

    AccesoProtegidoParticipacion acceso(Duration limite, Optional<FasesIntento> fases) {
        return ConfiguracionParticipacion.ensamblar(repositorio, participaciones, transacciones, identidad, titularidad,
                convocatoria -> ElegibilidadInicio.Resultado.ELEGIBLE, (convocatoria, inicio) -> Optional.of(inicio.plus(limite)),
                new CoordinacionInicioDesdeM4(coordinacion, identidad),
                new ConsultaContenidoIntentoTransaccional(repositorio, programacion, contenido), fases);
    }

    AccesoProtegidoParticipacion acceso() { return acceso(Duration.ofMinutes(30)); }

    ReferenciaIntento iniciar(AccesoProtegidoParticipacion acceso, Cadena c) {
        como(c);
        return acceso.iniciarOReanudar(c.referencia()).intento().referencia();
    }

    int filasFase(ReferenciaIntento intento) {
        return jdbc.queryForObject("SELECT count(*) FROM \"FaseIntento\" WHERE \"idIntento\"=?::uuid", Integer.class, intento.valor());
    }

    int respuestas(ReferenciaIntento intento) {
        return jdbc.queryForObject("SELECT count(*) FROM \"Respuesta\" WHERE \"idIntento\"=?::uuid", Integer.class, intento.valor());
    }

    static String sqlState(Throwable e) {
        for (Throwable t = e; t != null; t = t.getCause()) { if (t instanceof SQLException s) { return s.getSQLState(); } }
        return null;
    }

    void rechazado(String sql, Object... args) {
        Exception e = assertThrows(Exception.class, () -> jdbc.update(sql, args), sql);
        assertEquals("23514", sqlState(e), String.valueOf(e));
    }

    // --- B1: esquema y contratos -----------------------------------------------------------------------------------

    @Test @DisplayName("B1: el esquema se reconoce solo con sim-ab; el productivo, DD-02 B y sim-a lo rechazan")
    void contratoSimAb() {
        assertDoesNotThrow(() -> M4SchemaAutoConfiguration.verificar(jdbc, M4SchemaContract.V013_SIM_AB));
        for (String otro : List.of(M4SchemaContract.VIGENTE, M4SchemaContract.V013_SIM_A, M4SchemaContract.V013_DD02B,
                M4SchemaContract.V013_DD02B_SIM_AB)) {
            assertThrows(IllegalStateException.class, () -> M4SchemaAutoConfiguration.verificar(jdbc, otro), otro);
        }
        assertEquals("001,002,003,004,005,006,007,008,009,013,017,018", jdbc.queryForObject(
                "SELECT string_agg(version, ',' ORDER BY installed_rank) FROM flyway_schema_history", String.class));
    }

    @Test @DisplayName("B1: V018 solo admite altas en Exploracion, de un intento EnCurso y con un guion publicado del mismo caso")
    void integridadDelAlta() {
        Cadena sin = cadena(false);
        ReferenciaIntento intento = iniciar(acceso(), sin);
        assertEquals(0, filasFase(intento));
        Cadena otro = cadena(true);
        String alta = "INSERT INTO \"FaseIntento\" (\"idIntento\",\"idGuion\",\"fase\") VALUES (?::uuid,?,?)";
        String altaEnCuestionario = "INSERT INTO \"FaseIntento\" (\"idIntento\",\"idGuion\",\"fase\",\"fechaInicioCuestionario\")"
                + " VALUES (?::uuid,?,'Cuestionario',clock_timestamp()+interval '1 day')";
        // Guion de otro caso.
        rechazado(alta, intento.valor(), otro.guion().orElseThrow(), "Exploracion");
        // Guion en borrador del propio caso (antes del bloqueo no se puede programar; se crea uno para otro caso aún libre).
        UUID casoLibre = UUID.randomUUID();
        jdbc.update("INSERT INTO \"Caso\" (\"idCaso\",\"idDocente\",\"nombre\",\"tiempoEstimadoSegundos\",\"estado\") VALUES (?,?,'Libre B',600,'Publicado')",
                casoLibre, sin.docente());
        rechazado(alta, intento.valor(), guionPublicado(casoLibre, sin.docente(), "Borrador"), "Exploracion");
        // Alta directamente en Cuestionario: rechazada por el trigger aunque las fechas sean coherentes.
        Cadena conGuion = cadena(true);
        ReferenciaIntento sinFaseConGuion = iniciar(acceso(Duration.ofMinutes(30), Optional.empty()), conGuion);
        rechazado(altaEnCuestionario, sinFaseConGuion.valor(), conGuion.guion().orElseThrow());
        // Intento finalizado.
        acceso(Duration.ofMinutes(30), Optional.empty()).finalizarIntento(sinFaseConGuion, MotivoFinalizacion.ENTREGA_ESTUDIANTE);
        rechazado(alta, sinFaseConGuion.valor(), conGuion.guion().orElseThrow(), "Exploracion");
        assertEquals(0, filasFase(sinFaseConGuion));
    }

    @Test @DisplayName("B1: la fase nunca retrocede, no cambia su identidad, no cambia tras finalizar y no se borra ni se trunca")
    void integridadDeLosCambios() {
        Cadena c = cadena(true);
        AccesoProtegidoParticipacion acceso = acceso();
        ReferenciaIntento intento = iniciar(acceso, c);
        String id = intento.valor();
        // Coherencia fase/fecha (CHECK).
        rechazado("UPDATE \"FaseIntento\" SET \"fase\"='Cuestionario' WHERE \"idIntento\"=?::uuid", id);
        rechazado("UPDATE \"FaseIntento\" SET \"fechaInicioExploracion\"=clock_timestamp() WHERE \"idIntento\"=?::uuid", id);
        UUID otroGuion = cadena(true).guion().orElseThrow();
        rechazado("UPDATE \"FaseIntento\" SET \"idGuion\"=? WHERE \"idIntento\"=?::uuid", otroGuion, id);
        acceso.iniciarCuestionario(intento);
        rechazado("UPDATE \"FaseIntento\" SET \"fase\"='Exploracion', \"fechaInicioCuestionario\"=NULL WHERE \"idIntento\"=?::uuid", id);
        rechazado("UPDATE \"FaseIntento\" SET \"fechaInicioCuestionario\"=clock_timestamp() WHERE \"idIntento\"=?::uuid", id);
        rechazado("DELETE FROM \"FaseIntento\" WHERE \"idIntento\"=?::uuid", id);
        Exception truncado = assertThrows(Exception.class, () -> jdbc.execute("TRUNCATE \"FaseIntento\""));
        assertEquals("23514", sqlState(truncado));
        // Un intento finalizado conserva la fase en la que estaba.
        Cadena e = cadena(true);
        ReferenciaIntento enExploracion = iniciar(acceso, e);
        acceso.finalizarIntento(enExploracion, MotivoFinalizacion.ENTREGA_ESTUDIANTE);
        rechazado("UPDATE \"FaseIntento\" SET \"fase\"='Cuestionario', \"fechaInicioCuestionario\"=clock_timestamp() WHERE \"idIntento\"=?::uuid",
                enExploracion.valor());
        assertEquals("Exploracion", jdbc.queryForObject("SELECT \"fase\" FROM \"FaseIntento\" WHERE \"idIntento\"=?::uuid",
                String.class, enExploracion.valor()));
    }

    // --- B2a: creación, recuperación y compatibilidad -------------------------------------------------------------

    @Test @DisplayName("B2a: un caso con guion crea la fase al iniciar; reanudar no crea otra; un caso sin guion es SIN_FASES")
    void creacionEnElInicio() {
        AccesoProtegidoParticipacion acceso = acceso();
        Cadena c = cadena(true);
        ResultadoInicio inicio = acceso.iniciarOReanudar(conIdentidad(c));
        ReferenciaIntento intento = inicio.intento().referencia();
        assertFalse(inicio.reanudado());
        assertEquals(1, filasFase(intento));
        assertEquals(c.guion().orElseThrow(), jdbc.queryForObject(
                "SELECT \"idGuion\" FROM \"FaseIntento\" WHERE \"idIntento\"=?::uuid", UUID.class, intento.valor()));
        EstadoFase fase = acceso.consultarFase(intento);
        assertEquals(FaseAcademica.EXPLORACION, fase.fase());
        assertTrue(acceso.iniciarOReanudar(c.referencia()).reanudado());
        assertEquals(1, filasFase(intento));

        Cadena sin = cadena(false);
        ReferenciaIntento sinFases = iniciar(acceso, sin);
        assertEquals(0, filasFase(sinFases));
        EstadoFase estado = acceso.consultarFase(sinFases);
        assertEquals(FaseAcademica.SIN_FASES, estado.fase());
        assertEquals(EstadoIntento.EN_CURSO, estado.estadoIntento());
        assertFalse(estado.ahoraServidor().isAfter(estado.limiteTemporal()));
    }

    ReferenciaConvocatoria conIdentidad(Cadena c) { como(c); return c.referencia(); }

    @Test @DisplayName("B2a: si la creación de la fase falla, el inicio se revierte entero (ni participación ni intento)")
    void inicioAtomico() {
        Cadena c = cadena(true);
        UUID guionAjeno = cadena(true).guion().orElseThrow();
        GuionesDelIntento ajeno = new GuionesDelIntento() {
            @Override public Optional<UUID> publicadoParaConvocatoria(ReferenciaConvocatoria x) { return Optional.of(guionAjeno); }
            @Override public Optional<VersionGuion> version(UUID id) { return Optional.empty(); }
        };
        var acceso = acceso(Duration.ofMinutes(30), Optional.of(
                new FasesIntento(new RepositorioFasesJdbc(jdbc), ajeno, new PoliticasFaseAprobadas())));
        como(c);
        Exception e = assertThrows(Exception.class, () -> acceso.iniciarOReanudar(c.referencia()));
        assertEquals("23514", sqlState(e), String.valueOf(e));
        assertEquals(0, jdbc.queryForObject("SELECT count(*) FROM \"Participacion\" WHERE \"idConvocatoria\"=?", Integer.class, c.convocatoria()));
    }

    @Test @DisplayName("B2a compatibilidad: un intento creado sin fases conserva su comportamiento con las fases activas (C1/C2)")
    void intentoAnteriorSinFases() {
        Cadena c = cadena(true);
        ReferenciaIntento anterior = iniciar(acceso(Duration.ofMinutes(30), Optional.empty()), c);
        AccesoProtegidoParticipacion conFases = acceso();
        assertEquals(FaseAcademica.SIN_FASES, conFases.consultarFase(anterior).fase());
        assertTrue(conFases.iniciarOReanudar(c.referencia()).reanudado());
        assertEquals(0, filasFase(anterior), "reanudar no crea fases");
        assertEquals(1, conFases.guardarRespuesta(anterior, new ReferenciaPregunta(c.p1().toString()),
                new ValorRespuesta(c.o1().toString()), 0).revision());
        assertThrows(SinFasesException.class, () -> conFases.iniciarCuestionario(anterior));
        assertThrows(NarrativaNoDisponibleException.class, () -> conFases.consultarNarrativa(anterior));
        assertEquals(EstadoIntento.FINALIZADO, conFases.finalizarIntento(anterior, MotivoFinalizacion.ENTREGA_ESTUDIANTE).estado());
    }

    // --- B2a: R2, P3, concurrencia ---------------------------------------------------------------------------------

    @Test @DisplayName("R2: en exploración ninguna respuesta se registra ni se actualiza; tras P3 el guardado funciona como antes")
    void respuestasSoloEnCuestionario() {
        Cadena c = cadena(true);
        AccesoProtegidoParticipacion acceso = acceso();
        ReferenciaIntento intento = iniciar(acceso, c);
        var p1 = new ReferenciaPregunta(c.p1().toString());
        var o1 = new ValorRespuesta(c.o1().toString());
        assertThrows(CuestionarioNoIniciadoException.class, () -> acceso.guardarRespuesta(intento, p1, o1, 0));
        assertThrows(CuestionarioNoIniciadoException.class, () -> acceso.registrarRespuesta(intento, p1, o1));
        assertThrows(CuestionarioNoIniciadoException.class, () -> acceso.actualizarRespuesta(intento, p1, o1));
        assertEquals(0, respuestas(intento));
        EntradaCuestionario entrada = acceso.iniciarCuestionario(intento);
        assertFalse(entrada.yaIniciado());
        assertEquals(1, acceso.guardarRespuesta(intento, p1, o1, 0).revision());
        assertEquals(1, respuestas(intento));
    }

    @Test @DisplayName("P3 concurrente: 8 hilos y dos ensamblados (dos sesiones) producen una sola escritura y la misma fecha")
    void cuestionarioConcurrenteIdempotente() throws Exception {
        Cadena c = cadena(true);
        ReferenciaIntento intento = iniciar(acceso(), c);
        List<AccesoProtegidoParticipacion> sesiones = List.of(acceso(), acceso());
        ExecutorService hilos = Executors.newFixedThreadPool(8);
        CountDownLatch salida = new CountDownLatch(1);
        List<Future<EntradaCuestionario>> resultados = new ArrayList<>();
        try {
            for (int i = 0; i < 8; i++) {
                AccesoProtegidoParticipacion s = sesiones.get(i % 2);
                resultados.add(hilos.submit(() -> { salida.await(); return s.iniciarCuestionario(intento); }));
            }
            salida.countDown();
            List<EntradaCuestionario> todas = new ArrayList<>();
            for (var r : resultados) { todas.add(r.get(30, TimeUnit.SECONDS)); }
            assertEquals(1, todas.stream().filter(e -> !e.yaIniciado()).count());
            assertEquals(1, todas.stream().map(EntradaCuestionario::fechaInicioCuestionario).distinct().count());
            Instant persistida = jdbc.queryForObject("SELECT \"fechaInicioCuestionario\" FROM \"FaseIntento\" WHERE \"idIntento\"=?::uuid",
                    OffsetDateTime.class, intento.valor()).toInstant();
            assertEquals(persistida, todas.get(0).fechaInicioCuestionario());
        } finally {
            hilos.shutdownNow();
        }
    }

    @Test @DisplayName("P3 frente al PUT concurrente: una respuesta solo persiste si su escritura es posterior a la entrada al cuestionario")
    void cuestionarioFrenteARespuestaConcurrente() throws Exception {
        for (int ronda = 0; ronda < 6; ronda++) {
            Cadena c = cadena(true);
            AccesoProtegidoParticipacion acceso = acceso();
            ReferenciaIntento intento = iniciar(acceso, c);
            var p1 = new ReferenciaPregunta(c.p1().toString());
            var o1 = new ValorRespuesta(c.o1().toString());
            ExecutorService hilos = Executors.newFixedThreadPool(2);
            CountDownLatch salida = new CountDownLatch(1);
            try {
                Future<Boolean> respuesta = hilos.submit(() -> {
                    salida.await();
                    try { acceso.guardarRespuesta(intento, p1, o1, 0); return true; }
                    catch (CuestionarioNoIniciadoException enExploracion) { return false; }
                });
                Future<EntradaCuestionario> entrada = hilos.submit(() -> { salida.await(); return acceso.iniciarCuestionario(intento); });
                salida.countDown();
                boolean guardada = respuesta.get(30, TimeUnit.SECONDS);
                entrada.get(30, TimeUnit.SECONDS);
                assertEquals(guardada ? 1 : 0, respuestas(intento));
                if (guardada) {
                    Boolean posterior = jdbc.queryForObject("""
                            SELECT r."fechaActualizacion" >= f."fechaInicioCuestionario" FROM "Respuesta" r
                            JOIN "FaseIntento" f ON f."idIntento" = r."idIntento" WHERE r."idIntento"=?::uuid""",
                            Boolean.class, intento.valor());
                    assertTrue(posterior, "una respuesta nunca precede a la entrada al cuestionario");
                }
            } finally {
                hilos.shutdownNow();
            }
        }
    }

    void esperarVencimiento(AccesoProtegidoParticipacion acceso, ReferenciaIntento intento) throws InterruptedException {
        long hasta = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
        while (true) {
            EstadoFase f = acceso.consultarFase(intento);
            if (f.ahoraServidor().isAfter(f.limiteTemporal())) { return; }
            assertTrue(System.nanoTime() < hasta, "el límite de prueba no venció");
            Thread.sleep(100);
        }
    }

    @Test @DisplayName("D-SIM-3: tras el límite (reloj de PostgreSQL) P3 responde LIMITE_TEMPORAL_VENCIDO sin escribir; finalizado: INTENTO_FINALIZADO")
    void cuestionarioTrasElLimiteYFinalizado() throws Exception {
        Cadena c = cadena(true);
        AccesoProtegidoParticipacion acceso = acceso(Duration.ofSeconds(1));
        ReferenciaIntento intento = iniciar(acceso, c);
        esperarVencimiento(acceso, intento);
        assertThrows(LimiteTemporalVencidoException.class, () -> acceso.iniciarCuestionario(intento));
        assertThrows(LimiteTemporalVencidoException.class, () -> acceso.iniciarCuestionario(intento));
        assertEquals(FaseAcademica.EXPLORACION, acceso.consultarFase(intento).fase());
        assertEquals(EstadoIntento.EN_CURSO, acceso.consultarFase(intento).estadoIntento(), "no hay cierre automático por vencimiento");
        assertEquals(null, jdbc.queryForObject("SELECT \"fechaInicioCuestionario\" FROM \"FaseIntento\" WHERE \"idIntento\"=?::uuid",
                OffsetDateTime.class, intento.valor()));
        acceso.finalizarIntento(intento, MotivoFinalizacion.ENTREGA_ESTUDIANTE);
        assertThrows(IntentoFinalizadoException.class, () -> acceso.iniciarCuestionario(intento));
    }

    @Test @DisplayName("D-SIM-3: una entrada confirmada antes del límite no se revierte con reintentos posteriores al vencimiento")
    void reintentoTrasElLimiteConservaLaEntrada() throws Exception {
        Cadena c = cadena(true);
        AccesoProtegidoParticipacion acceso = acceso(Duration.ofSeconds(1));
        ReferenciaIntento intento = iniciar(acceso, c);
        EntradaCuestionario entrada = acceso.iniciarCuestionario(intento);
        assertFalse(entrada.yaIniciado());
        esperarVencimiento(acceso, intento);
        for (int i = 0; i < 3; i++) {
            EntradaCuestionario reintento = acceso.iniciarCuestionario(intento);
            assertTrue(reintento.yaIniciado());
            assertEquals(entrada.fechaInicioCuestionario(), reintento.fechaInicioCuestionario());
        }
        assertEquals(FaseAcademica.CUESTIONARIO, acceso.consultarFase(intento).fase());
    }

    @Test @DisplayName("D-SIM-3 en carrera con el vencimiento: a lo sumo una entrada, nunca posterior al límite; el resto vencido o idempotente")
    void cuestionarioEnCarreraConElLimite() throws Exception {
        for (int ronda = 0; ronda < 4; ronda++) {
            Cadena c = cadena(true);
            AccesoProtegidoParticipacion acceso = acceso(Duration.ofMillis(700));
            ReferenciaIntento intento = iniciar(acceso, c);
            ExecutorService hilos = Executors.newFixedThreadPool(8);
            CountDownLatch salida = new CountDownLatch(1);
            List<Future<String>> resultados = new ArrayList<>();
            try {
                for (int i = 0; i < 8; i++) {
                    long espera = 250L * i; // 0 a 1750 ms: unos antes y otros después del límite de 700 ms
                    resultados.add(hilos.submit(() -> {
                        salida.await();
                        Thread.sleep(espera);
                        try { return acceso.iniciarCuestionario(intento).yaIniciado() ? "idempotente" : "entrada"; }
                        catch (LimiteTemporalVencidoException vencido) { return "vencido"; }
                    }));
                }
                salida.countDown();
                List<String> todos = new ArrayList<>();
                for (var r : resultados) { todos.add(r.get(30, TimeUnit.SECONDS)); }
                long entradas = todos.stream().filter("entrada"::equals).count();
                assertTrue(entradas <= 1, todos.toString());
                Boolean dentro = jdbc.queryForObject("""
                        SELECT f."fechaInicioCuestionario" IS NULL OR f."fechaInicioCuestionario" <= i."limiteTemporal"
                        FROM "FaseIntento" f JOIN "Intento" i ON i."idIntento" = f."idIntento" WHERE f."idIntento"=?::uuid""",
                        Boolean.class, intento.valor());
                assertTrue(dentro, "una entrada confirmada nunca es posterior al límite");
                if (entradas == 0) {
                    assertFalse(todos.contains("idempotente"), todos.toString());
                    assertEquals(FaseAcademica.EXPLORACION, acceso.consultarFase(intento).fase());
                }
            } finally {
                hilos.shutdownNow();
            }
        }
    }

    // --- B2a: P1, recuperación, P4 y titularidad -------------------------------------------------------------------

    @Test @DisplayName("P1 sirve la versión fijada con su huella; se cierra en el cuestionario (R4); D-SIM-10: finalizado, EXPLORACION_CERRADA")
    void narrativaDelIntento() {
        Cadena c = cadena(true);
        AccesoProtegidoParticipacion acceso = acceso();
        ReferenciaIntento intento = iniciar(acceso, c);
        NarrativaIntento n = acceso.consultarNarrativa(intento);
        assertEquals(c.guion().orElseThrow(), n.idGuion());
        assertEquals(1, n.version());
        assertEquals(jdbc.queryForObject("SELECT \"hashDefinicion\" FROM \"GuionNarrativoCaso\" WHERE \"idGuion\"=?",
                String.class, n.idGuion()), n.hashDefinicion());
        assertTrue(n.definicionJson().contains("prisaly-guion-1"));
        acceso.iniciarCuestionario(intento);
        assertThrows(ExploracionCerradaException.class, () -> acceso.consultarNarrativa(intento));
        acceso.finalizarIntento(intento, MotivoFinalizacion.ENTREGA_ESTUDIANTE);
        assertThrows(ExploracionCerradaException.class, () -> acceso.consultarNarrativa(intento));
        // Finalizado desde la exploración: también cerrada, sin reabrir ni cambiar la fase.
        Cadena e = cadena(true);
        ReferenciaIntento explorado = iniciar(acceso, e);
        acceso.consultarNarrativa(explorado);
        acceso.finalizarIntento(explorado, MotivoFinalizacion.ENTREGA_ESTUDIANTE);
        assertThrows(ExploracionCerradaException.class, () -> acceso.consultarNarrativa(explorado));
        assertEquals(FaseAcademica.EXPLORACION, acceso.consultarFase(explorado).fase());
        assertEquals(EstadoIntento.FINALIZADO, acceso.consultarFase(explorado).estadoIntento());
    }

    @Test @DisplayName("recuperación: otro ensamblado (recarga o nueva sesión) lee la misma fase, fecha y versión desde el servidor")
    void recuperacionDeLaFase() {
        Cadena c = cadena(true);
        ReferenciaIntento intento = iniciar(acceso(), c);
        NarrativaIntento antes = acceso().consultarNarrativa(intento);
        EntradaCuestionario entrada = acceso().iniciarCuestionario(intento);
        actual.set(Optional.empty());
        como(c);
        AccesoProtegidoParticipacion recargado = acceso();
        EstadoFase fase = recargado.consultarFase(intento);
        assertEquals(FaseAcademica.CUESTIONARIO, fase.fase());
        assertEquals(Optional.of(entrada.fechaInicioCuestionario()), fase.fechaInicioCuestionario());
        assertTrue(recargado.iniciarCuestionario(intento).yaIniciado());
        assertEquals(c.guion().orElseThrow(), antes.idGuion());
    }

    @Test @DisplayName("P4 informa el recuento sobre el contenido autorizado de M3, sin criterio ni aciertos; D-SIM-2: nada en exploración")
    void completitud() {
        Cadena c = cadena(true);
        AccesoProtegidoParticipacion acceso = acceso();
        ReferenciaIntento intento = iniciar(acceso, c);
        assertThrows(CuestionarioNoIniciadoException.class, () -> acceso.consultarCompletitud(intento));
        acceso.iniciarCuestionario(intento);
        CompletitudIntento inicial = acceso.consultarCompletitud(intento);
        assertEquals(2, inicial.preguntasPresentables());
        assertEquals(0, inicial.respondidas());
        assertEquals(List.of(c.p1(), c.p2()), inicial.sinResponder());
        acceso.guardarRespuesta(intento, new ReferenciaPregunta(c.p1().toString()), new ValorRespuesta(c.o1().toString()), 0);
        CompletitudIntento despues = acceso.consultarCompletitud(intento);
        assertEquals(1, despues.respondidas());
        assertEquals(List.of(c.p2()), despues.sinResponder());
    }

    @Test @DisplayName("D-SIM-2: contenido y P4 ocultos en exploración y tras finalizar sin cuestionario; visibles tras entrar; SIN_FASES intacto")
    void preguntasOcultasSinCuestionario() {
        AccesoProtegidoParticipacion acceso = acceso();
        // En exploración.
        Cadena c = cadena(true);
        ReferenciaIntento explorado = iniciar(acceso, c);
        assertThrows(CuestionarioNoIniciadoException.class, () -> acceso.consultarContenido(explorado));
        assertThrows(CuestionarioNoIniciadoException.class, () -> acceso.consultarCompletitud(explorado));
        // Finalizado desde la exploración: siguen ocultas.
        acceso.finalizarIntento(explorado, MotivoFinalizacion.ENTREGA_ESTUDIANTE);
        assertThrows(CuestionarioNoIniciadoException.class, () -> acceso.consultarContenido(explorado));
        assertThrows(CuestionarioNoIniciadoException.class, () -> acceso.consultarCompletitud(explorado));
        assertEquals(0, respuestas(explorado));
        // Tras entrar al cuestionario: el contenido es el mismo que antes de las fases; también después de finalizar.
        Cadena q = cadena(true);
        ReferenciaIntento cuestionario = iniciar(acceso, q);
        acceso.iniciarCuestionario(cuestionario);
        var contenido = acceso.consultarContenido(cuestionario);
        assertEquals(2, contenido.caso().escenarios().get(0).preguntas().size());
        acceso.finalizarIntento(cuestionario, MotivoFinalizacion.ENTREGA_ESTUDIANTE);
        assertEquals(contenido, acceso.consultarContenido(cuestionario));
        assertEquals(2, acceso.consultarCompletitud(cuestionario).preguntasPresentables());
        // SIN_FASES (caso sin guion) con las fases activas: sin cambios.
        Cadena s = cadena(false);
        ReferenciaIntento sinFases = iniciar(acceso, s);
        assertEquals(2, acceso.consultarContenido(sinFases).caso().escenarios().get(0).preguntas().size());
        assertEquals(2, acceso.consultarCompletitud(sinFases).preguntasPresentables());
        // Un intento ajeno sigue siendo 403 antes de mirar la fase (no revela si está en exploración).
        como(s);
        assertThrows(AccesoIntentoDenegadoException.class, () -> acceso.consultarContenido(explorado));
    }

    @Test @DisplayName("D-SIM-4: finalizar desde exploración es posible, idempotente, conserva la fase y no califica; titularidad intacta")
    void finalizacionDesdeExploracion() {
        AccesoProtegidoParticipacion acceso = acceso();
        Cadena c = cadena(true);
        Cadena otro = cadena(true);
        ReferenciaIntento intento = iniciar(acceso, c);
        como(otro);
        assertThrows(AccesoIntentoDenegadoException.class, () -> acceso.finalizarIntento(intento, MotivoFinalizacion.ENTREGA_ESTUDIANTE));
        actual.set(Optional.empty());
        assertThrows(EstudianteNoIdentificadoException.class, () -> acceso.finalizarIntento(intento, MotivoFinalizacion.ENTREGA_ESTUDIANTE));
        como(c);
        assertEquals(EstadoIntento.EN_CURSO, acceso.consultarFase(intento).estadoIntento());
        var fin = acceso.finalizarIntento(intento, MotivoFinalizacion.ENTREGA_ESTUDIANTE);
        assertEquals(EstadoIntento.FINALIZADO, fin.estado());
        var repetido = acceso.finalizarIntento(intento, MotivoFinalizacion.ENTREGA_ESTUDIANTE);
        assertEquals(fin.tiempos().orElseThrow().fin(), repetido.tiempos().orElseThrow().fin(), "idempotente: mismo cierre");
        assertEquals("Exploracion", jdbc.queryForObject("SELECT \"fase\" FROM \"FaseIntento\" WHERE \"idIntento\"=?::uuid",
                String.class, intento.valor()));
        assertEquals(0, jdbc.queryForObject("SELECT count(*) FROM \"Calificacion\" WHERE \"idIntento\"=?::uuid", Integer.class,
                intento.valor()), "sin calificación automática");
        // Sin nuevas interacciones académicas.
        assertThrows(IntentoFinalizadoException.class, () -> acceso.iniciarCuestionario(intento));
        assertThrows(RuntimeException.class, () -> acceso.guardarRespuesta(intento, new ReferenciaPregunta(c.p1().toString()),
                new ValorRespuesta(c.o1().toString()), 0));
        assertEquals(0, respuestas(intento));
    }

    @Test @DisplayName("finalizar frente a P3 concurrente: estado final coherente (cuestionario antes del fin, o exploración finalizada)")
    void finalizacionFrenteACuestionarioConcurrente() throws Exception {
        for (int ronda = 0; ronda < 6; ronda++) {
            Cadena c = cadena(true);
            AccesoProtegidoParticipacion acceso = acceso();
            ReferenciaIntento intento = iniciar(acceso, c);
            ExecutorService hilos = Executors.newFixedThreadPool(2);
            CountDownLatch salida = new CountDownLatch(1);
            try {
                Future<Boolean> entrada = hilos.submit(() -> {
                    salida.await();
                    try { acceso.iniciarCuestionario(intento); return true; }
                    catch (IntentoFinalizadoException finalizado) { return false; }
                });
                Future<?> fin = hilos.submit(() -> { salida.await(); return acceso.finalizarIntento(intento, MotivoFinalizacion.ENTREGA_ESTUDIANTE); });
                salida.countDown();
                boolean entro = entrada.get(30, TimeUnit.SECONDS);
                fin.get(30, TimeUnit.SECONDS);
                EstadoFase f = acceso.consultarFase(intento);
                assertEquals(EstadoIntento.FINALIZADO, f.estadoIntento());
                assertEquals(entro ? FaseAcademica.CUESTIONARIO : FaseAcademica.EXPLORACION, f.fase());
                if (entro) {
                    Boolean antes = jdbc.queryForObject("""
                            SELECT f."fechaInicioCuestionario" <= i."fin" FROM "FaseIntento" f
                            JOIN "Intento" i ON i."idIntento" = f."idIntento" WHERE f."idIntento"=?::uuid""", Boolean.class, intento.valor());
                    assertTrue(antes);
                }
            } finally {
                hilos.shutdownNow();
            }
        }
    }

    @Test @DisplayName("D-SIM-3 (QA33-03): si el límite vence entre la lectura bloqueada y la escritura, P3 se rechaza y la fase sigue en exploración")
    void vencimientoEntreLecturaYEscritura() throws Exception {
        Cadena c = cadena(true);
        ReferenciaIntento intento = iniciar(acceso(Duration.ofSeconds(3)), c);
        RepositorioFases real = new RepositorioFasesJdbc(jdbc);
        AtomicReference<Boolean> leidaAntesDelLimite = new AtomicReference<>();
        // Decorador exclusivo del ensayo: tras la lectura bloqueada real, pausa (con el bloqueo tomado) hasta que el reloj de
        // PostgreSQL supere el límite; después delega la escritura real sin cambios. Fuerza la ventana de forma reproducible.
        RepositorioFases conPausa = (RepositorioFases) Proxy.newProxyInstance(RepositorioFases.class.getClassLoader(),
                new Class<?>[] {RepositorioFases.class}, (p, m, a) -> {
                    Object r;
                    try { r = m.invoke(real, a); } catch (InvocationTargetException e) { throw e.getCause(); }
                    if (m.getName().equals("bloquear")) {
                        var fila = ((Optional<?>) r).map(RepositorioFases.IntentoConFase.class::cast).orElseThrow();
                        leidaAntesDelLimite.set(!fila.ahora().isAfter(fila.limiteTemporal()));
                        long hasta = System.nanoTime() + TimeUnit.SECONDS.toNanos(15);
                        while (!Boolean.TRUE.equals(jdbc.queryForObject(
                                "SELECT clock_timestamp() > \"limiteTemporal\" FROM \"Intento\" WHERE \"idIntento\"=?::uuid",
                                Boolean.class, intento.valor()))) {
                            assertTrue(System.nanoTime() < hasta, "el límite del ensayo no venció");
                            Thread.sleep(50);
                        }
                    }
                    return r;
                });
        GuionesDelIntento sinUso = new GuionesDelIntento() {
            @Override public Optional<UUID> publicadoParaConvocatoria(ReferenciaConvocatoria x) { return Optional.empty(); }
            @Override public Optional<VersionGuion> version(UUID id) { return Optional.empty(); }
        };
        AccesoProtegidoParticipacion acceso = acceso(Duration.ofSeconds(3),
                Optional.of(new FasesIntento(conPausa, sinUso, new PoliticasFaseAprobadas())));
        como(c);
        assertThrows(LimiteTemporalVencidoException.class, () -> acceso.iniciarCuestionario(intento));
        assertTrue(leidaAntesDelLimite.get(), "la lectura bloqueada ocurrió antes del límite: la ventana se ejerció");
        assertEquals("Exploracion", jdbc.queryForObject("SELECT \"fase\" FROM \"FaseIntento\" WHERE \"idIntento\"=?::uuid",
                String.class, intento.valor()));
        assertEquals(null, jdbc.queryForObject("SELECT \"fechaInicioCuestionario\" FROM \"FaseIntento\" WHERE \"idIntento\"=?::uuid",
                OffsetDateTime.class, intento.valor()));
        assertEquals(EstadoIntento.EN_CURSO, acceso.consultarFase(intento).estadoIntento(), "sin cierre automático");
    }

    @Test @DisplayName("titularidad: las cuatro operaciones pasan por el Proxy; sin identidad o con un intento ajeno no se delega")
    void titularidad() {
        Cadena ana = cadena(true);
        Cadena luis = cadena(true);
        AccesoProtegidoParticipacion acceso = acceso();
        ReferenciaIntento deAna = iniciar(acceso, ana);
        como(luis);
        assertThrows(AccesoIntentoDenegadoException.class, () -> acceso.consultarFase(deAna));
        assertThrows(AccesoIntentoDenegadoException.class, () -> acceso.iniciarCuestionario(deAna));
        assertThrows(AccesoIntentoDenegadoException.class, () -> acceso.consultarNarrativa(deAna));
        assertThrows(AccesoIntentoDenegadoException.class, () -> acceso.consultarCompletitud(deAna));
        actual.set(Optional.empty());
        assertThrows(EstudianteNoIdentificadoException.class, () -> acceso.consultarFase(deAna));
        assertThrows(EstudianteNoIdentificadoException.class, () -> acceso.iniciarCuestionario(deAna));
        assertEquals("Exploracion", jdbc.queryForObject("SELECT \"fase\" FROM \"FaseIntento\" WHERE \"idIntento\"=?::uuid",
                String.class, deAna.valor()));
    }

    @Test @DisplayName("el bean productivo con fases activas sigue sin permitir el primer inicio (políticas no ratificadas)")
    void beanProductivoNoIniciaIntentos() {
        Cadena c = cadena(true);
        // La identidad productiva es la de M1 con DD-02 desactivado: deniega antes de cualquier escritura.
        assertThrows(RuntimeException.class, () -> produccion.iniciarOReanudar(c.referencia()));
        assertEquals(0, jdbc.queryForObject("SELECT count(*) FROM \"Participacion\" WHERE \"idConvocatoria\"=?", Integer.class, c.convocatoria()));
    }
}
