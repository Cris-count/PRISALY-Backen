package com.prisaly.m5.infrastructure.persistencia;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.persistence.autoconfigure.EntityScan;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.IllegalTransactionStateException;
import org.springframework.transaction.PlatformTransactionManager;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import com.prisaly.m1.application.service.DenyingStudentAccessService;
import com.prisaly.m4.api.ConsultaProgramacionNoDisponibleException;
import com.prisaly.m4.api.ContextoProgramacion;
import com.prisaly.m4.api.ProgramacionParticipacionPort;
import com.prisaly.m4.infrastructure.adapter.ProgramacionParticipacionJdbcAdapter;
import com.prisaly.m5.application.ConvocatoriaNoEncontradaException;
import com.prisaly.m5.application.ReferenciaConvocatoria;
import com.prisaly.m5.application.RepeticionNoHabilitadaException;
import com.prisaly.m5.application.ResultadoInicio;
import com.prisaly.m5.application.acceso.AccesoProtegidoParticipacion;
import com.prisaly.m5.application.acceso.ControlAccesoIntentos;
import com.prisaly.m5.application.acceso.EstudianteNoIdentificadoException;
import com.prisaly.m5.application.acceso.IdentidadEstudianteActual;
import com.prisaly.m5.application.acceso.IdentificadorEstudiante;
import com.prisaly.m5.application.acceso.TitularidadIntentos;
import com.prisaly.m5.application.inicio.DecisionPendiente;
import com.prisaly.m5.application.inicio.InicioNoDisponibleException;
import com.prisaly.m5.application.inicio.LimiteTemporalInicio;
import com.prisaly.m5.application.port.RepositorioIntentos;
import com.prisaly.m5.application.port.RepositorioParticipaciones;
import com.prisaly.m5.domain.intento.EstadoIntento;
import com.prisaly.m5.domain.intento.MotivoFinalizacion;
import com.prisaly.m5.domain.respuesta.Respuesta;
import com.prisaly.m5.infrastructure.ConfiguracionParticipacion;
import com.prisaly.m5.infrastructure.identidad.IdentidadEstudianteDesdeM1;
import com.prisaly.m5.infrastructure.programacion.TitularidadIntentosDesdeM4;

/**
 * Inicio y reanudación de intentos en PostgreSQL 16.15 con V001–V009, la
 * titularidad real de M4 ({@link ProgramacionParticipacionJdbcAdapter}) y el
 * proxy de protección.
 *
 * <p>Dos ensamblados:
 * <ul>
 *   <li>{@code produccion}: el bean de {@link ConfiguracionParticipacion}, con
 *       elegibilidad y límite NO ratificados. Debe bloquear el primer inicio y
 *       permitir la reanudación.</li>
 *   <li>{@link #accesoConPoliticasDePrueba}: el mismo mecanismo con
 *       {@link PoliticasInicioDePrueba}, dobles que permiten ejercitar la
 *       creación. No son reglas académicas.</li>
 * </ul>
 * La identidad es un doble ({@code IdentidadDePrueba}) porque DD-02 sigue
 * abierta. Requiere Docker: {@code mvnw -pl m5 -am -Pdocker-it verify}.
 */
@SpringBootTest(classes = InicioIntentoIT.AppPrueba.class)
@Testcontainers
@DisplayName("Inicio y reanudación de intentos en PostgreSQL 16.15")
class InicioIntentoIT {

    @Container static PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:16.15");

    @DynamicPropertySource
    static void propiedades(DynamicPropertyRegistry registro) {
        registro.add("spring.datasource.url", postgres::getJdbcUrl);
        registro.add("spring.datasource.username", postgres::getUsername);
        registro.add("spring.datasource.password", postgres::getPassword);
        registro.add("spring.datasource.hikari.maximum-pool-size", () -> "12");
        registro.add("spring.jpa.hibernate.ddl-auto", () -> "validate");
        registro.add("spring.jpa.open-in-view", () -> "false");
        registro.add("spring.jpa.properties.hibernate.globally_quoted_identifiers", () -> "true");
        registro.add("spring.flyway.enabled", () -> "true");
        registro.add("spring.flyway.locations", RepositorioIntentosJpaIT::ubicacionFlywayCanonica);
    }

    private static final Duration LIMITE_DE_PRUEBA = Duration.ofMinutes(30);

    @Autowired private AccesoProtegidoParticipacion produccion;

    @Autowired private AccesoEstudianteM4M5IT.IdentidadDePrueba identidad;

    @Autowired private TitularidadIntentos titularidad;

    @Autowired private RepositorioIntentos repositorio;

    @Autowired private RepositorioParticipaciones participaciones;

    @Autowired private JdbcTemplate jdbc;

    @Autowired private PlatformTransactionManager transacciones;

    private CadenaIntentoPrueba ana;
    private CadenaIntentoPrueba luis;

    @BeforeEach
    void crearCadenas() {
        ana = CadenaIntentoPrueba.crearHastaConvocatoria(jdbc);
        luis = CadenaIntentoPrueba.crearHastaConvocatoria(jdbc);
        identidad.actual = Optional.empty();
    }

    private void autenticar(CadenaIntentoPrueba cadena) {
        identidad.actual = Optional.of(new IdentificadorEstudiante(cadena.estudiante().toString()));
    }

    private AccesoProtegidoParticipacion accesoConPoliticasDePrueba(LimiteTemporalInicio limite) {
        return accesoCon(identidad, titularidad, limite);
    }

    private AccesoProtegidoParticipacion accesoCon(
            IdentidadEstudianteActual identidadActual, TitularidadIntentos titularidadActual, LimiteTemporalInicio limite) {
        return new AccesoProtegidoParticipacion(
                new ControlAccesoIntentos(identidadActual, titularidadActual),
                FachadaSinControlDePrueba.ensamblar(repositorio, participaciones, transacciones,
                        PoliticasInicioDePrueba.elegibleSiempre(), limite));
    }

    private AccesoProtegidoParticipacion autorizado() {
        return accesoConPoliticasDePrueba(PoliticasInicioDePrueba.limiteFijo(LIMITE_DE_PRUEBA));
    }

    // --- Primer inicio -------------------------------------------------------------

    @Test
    @DisplayName("primer inicio: crea la participación y el intento 1, vinculados a la convocatoria del estudiante")
    void primerInicio() {
        autenticar(ana);

        ResultadoInicio resultado = autorizado().iniciarOReanudar(ana.referenciaConvocatoria());

        assertFalse(resultado.reanudado());
        assertEquals(EstadoIntento.EN_CURSO, resultado.intento().estado());
        assertEquals(List.of(), resultado.intento().respuestas());
        assertEquals(LIMITE_DE_PRUEBA, Duration.between(resultado.inicio(), resultado.limiteTemporal()));

        Map<String, Object> participacion = jdbc.queryForMap(
                "SELECT * FROM \"Participacion\" WHERE \"idConvocatoria\" = ?", ana.convocatoria());
        // Estado por defecto de V001; M5 no escribe estados ni fechas de participación (pendientes).
        assertEquals("Pendiente", participacion.get("estado"));
        assertNull(participacion.get("fechaInicio"));
        assertNull(participacion.get("fechaFin"));
        assertNull(participacion.get("calificacionFinal"));

        Map<String, Object> intento = jdbc.queryForMap(
                "SELECT * FROM \"Intento\" WHERE \"idIntento\" = ?",
                UUID.fromString(resultado.intento().referencia().valor()));
        assertEquals(participacion.get("idParticipacion"), intento.get("idParticipacion"));
        assertEquals(1, intento.get("numeroIntento"));
        assertEquals("EnCurso", intento.get("estado"));
        assertEquals("Pendiente", intento.get("estadoEvaluacion"));
        assertNull(intento.get("idAutorizacion"));
        assertNull(intento.get("fin"));
        assertEquals(resultado.inicio(), instante(intento.get("inicio")));
        assertEquals(resultado.limiteTemporal(), instante(intento.get("limiteTemporal")));

        // La cadena llega al estudiante autenticado a través de M4.
        UUID estudiante = jdbc.queryForObject("""
                SELECT c."idEstudiante" FROM "Intento" i
                JOIN "Participacion" p ON p."idParticipacion" = i."idParticipacion"
                JOIN "Convocatoria" c ON c."idConvocatoria" = p."idConvocatoria"
                WHERE i."idIntento" = ?""", UUID.class, UUID.fromString(resultado.intento().referencia().valor()));
        assertEquals(ana.estudiante(), estudiante);
    }

    @Test
    @DisplayName("ensamblado productivo: el primer inicio falla por elegibilidad no ratificada y no escribe")
    void primerInicioBloqueadoEnProduccion() {
        autenticar(ana);

        InicioNoDisponibleException error = assertThrows(InicioNoDisponibleException.class,
                () -> produccion.iniciarOReanudar(ana.referenciaConvocatoria()));

        assertEquals(DecisionPendiente.ELEGIBILIDAD_ACADEMICA, error.decision());
        assertEquals(0, participacionesDe(ana.convocatoria()));
    }

    @Test
    @DisplayName("rollback: si el intento viola una restricción, tampoco queda la participación")
    void rollbackSiFallaElIntento() {
        autenticar(ana);

        assertThrows(DataIntegrityViolationException.class,
                () -> accesoConPoliticasDePrueba(PoliticasInicioDePrueba.limiteInvalido())
                        .iniciarOReanudar(ana.referenciaConvocatoria()));

        assertEquals(0, participacionesDe(ana.convocatoria()));
        assertEquals(0, intentosDe(ana.convocatoria()));
    }

    // --- Reanudación ----------------------------------------------------------------

    @Test
    @DisplayName("reanudación: mismo intento, mismas respuestas, mismo inicio y mismo límite; sin otro intento")
    void reanudacion() {
        autenticar(ana);
        AccesoProtegidoParticipacion acceso = autorizado();
        ResultadoInicio primero = acceso.iniciarOReanudar(ana.referenciaConvocatoria());
        acceso.registrarRespuesta(primero.intento().referencia(), ana.pregunta1(), ana.opcion1a());
        Map<String, Object> antes = filaIntento(primero);

        ResultadoInicio segundo = acceso.iniciarOReanudar(ana.referenciaConvocatoria());

        assertTrue(segundo.reanudado());
        assertEquals(primero.intento().referencia(), segundo.intento().referencia());
        assertEquals(List.of(new Respuesta(ana.pregunta1(), ana.opcion1a())), segundo.intento().respuestas());
        assertEquals(primero.inicio(), segundo.inicio());
        assertEquals(primero.limiteTemporal(), segundo.limiteTemporal());
        assertEquals(antes, filaIntento(segundo));
        assertEquals(1, participacionesDe(ana.convocatoria()));
        assertEquals(1, intentosDe(ana.convocatoria()));
    }

    @Test
    @DisplayName("ensamblado productivo: reanuda un intento en curso existente sin modificarlo")
    void reanudacionEnProduccion() {
        CadenaIntentoPrueba conIntento = CadenaIntentoPrueba.crear(jdbc);
        autenticar(conIntento);
        Map<String, Object> antes = jdbc.queryForMap(
                "SELECT * FROM \"Intento\" WHERE \"idIntento\" = ?", conIntento.idIntento());

        ResultadoInicio resultado = produccion.iniciarOReanudar(conIntento.referenciaConvocatoria());

        assertTrue(resultado.reanudado());
        assertEquals(conIntento.intento(), resultado.intento().referencia());
        assertEquals(instante(antes.get("inicio")), resultado.inicio());
        assertEquals(instante(antes.get("limiteTemporal")), resultado.limiteTemporal());
        assertEquals(antes, jdbc.queryForMap(
                "SELECT * FROM \"Intento\" WHERE \"idIntento\" = ?", conIntento.idIntento()));
        assertEquals(1, intentosDe(conIntento.convocatoria()));
    }

    @Test
    @DisplayName("intento finalizado: no se reanuda ni se crea otro (repetición no habilitada)")
    void intentoFinalizado() {
        autenticar(ana);
        AccesoProtegidoParticipacion acceso = autorizado();
        ResultadoInicio inicio = acceso.iniciarOReanudar(ana.referenciaConvocatoria());
        acceso.finalizarIntento(inicio.intento().referencia(), MotivoFinalizacion.ENTREGA_ESTUDIANTE);

        assertThrows(RepeticionNoHabilitadaException.class, () -> acceso.iniciarOReanudar(ana.referenciaConvocatoria()));
        assertThrows(RepeticionNoHabilitadaException.class, () -> produccion.iniciarOReanudar(ana.referenciaConvocatoria()));

        assertEquals(1, intentosDe(ana.convocatoria()));
        assertEquals("Finalizado", filaIntento(inicio).get("estado"));
    }

    @Test
    @DisplayName("V005: un intento previo sobre el mismo caso en otra sesión exige autorización; no queda nada creado")
    void repeticionEntreSesiones() {
        CadenaIntentoPrueba conIntento = CadenaIntentoPrueba.crear(jdbc);
        UUID otraSesion = UUID.randomUUID();
        UUID otraConvocatoria = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO "Sesion" ("idSesion", "idCaso", "idDocenteProgramador", "nombreEspacioAcademico",
                    "inicioPrevisto", "finPrevisto", "tiempoMaximoParticipacionSegundos")
                VALUES (?, ?, ?, 'Otra sesión fixture M5', now(), now() + interval '1 hour', 3600)""",
                otraSesion, conIntento.caso(), conIntento.docente());
        jdbc.update("INSERT INTO \"Convocatoria\" (\"idConvocatoria\", \"idSesion\", \"idEstudiante\") VALUES (?, ?, ?)",
                otraConvocatoria, otraSesion, conIntento.estudiante());
        autenticar(conIntento);

        assertThrows(RepeticionNoHabilitadaException.class,
                () -> autorizado().iniciarOReanudar(new ReferenciaConvocatoria(otraConvocatoria.toString())));

        assertEquals(0, participacionesDe(otraConvocatoria));
    }

    // --- Seguridad --------------------------------------------------------------------

    @Test
    @DisplayName("estudiante ajeno: no inicia ni reanuda la convocatoria de otro, y no se crea nada")
    void estudianteAjeno() {
        autenticar(ana);
        ResultadoInicio deAna = autorizado().iniciarOReanudar(ana.referenciaConvocatoria());
        autenticar(luis);

        assertThrows(ConvocatoriaNoEncontradaException.class,
                () -> autorizado().iniciarOReanudar(ana.referenciaConvocatoria()));
        assertThrows(ConvocatoriaNoEncontradaException.class,
                () -> produccion.iniciarOReanudar(ana.referenciaConvocatoria()));

        assertEquals(1, intentosDe(ana.convocatoria()));
        assertEquals(deAna.intento().referencia().valor(),
                jdbc.queryForObject("""
                        SELECT i."idIntento"::text FROM "Intento" i
                        JOIN "Participacion" p ON p."idParticipacion" = i."idParticipacion"
                        WHERE p."idConvocatoria" = ?""", String.class, ana.convocatoria()));
        assertEquals(0, participacionesDe(luis.convocatoria()));
    }

    @Test
    @DisplayName("convocatoria inexistente o referencia inválida: mismo error, sin escrituras")
    void convocatoriaInexistente() {
        autenticar(ana);
        UUID inexistente = UUID.randomUUID();

        assertThrows(ConvocatoriaNoEncontradaException.class,
                () -> autorizado().iniciarOReanudar(new ReferenciaConvocatoria(inexistente.toString())));
        assertThrows(ConvocatoriaNoEncontradaException.class,
                () -> autorizado().iniciarOReanudar(new ReferenciaConvocatoria("no-es-uuid")));

        assertEquals(0, participacionesDe(inexistente));
    }

    @Test
    @DisplayName("sin identidad: no se inicia ni se reanuda")
    void sinIdentidad() {
        assertThrows(EstudianteNoIdentificadoException.class,
                () -> autorizado().iniciarOReanudar(ana.referenciaConvocatoria()));
        assertThrows(EstudianteNoIdentificadoException.class,
                () -> produccion.iniciarOReanudar(ana.referenciaConvocatoria()));
        assertEquals(0, participacionesDe(ana.convocatoria()));
    }

    @Test
    @DisplayName("M1 real (DD-02 abierta): toda solicitud se deniega sin escrituras")
    void m1RealDeniega() {
        AccesoProtegidoParticipacion conM1 = accesoCon(
                new IdentidadEstudianteDesdeM1(new DenyingStudentAccessService()), titularidad,
                PoliticasInicioDePrueba.limiteFijo(LIMITE_DE_PRUEBA));

        assertThrows(EstudianteNoIdentificadoException.class, () -> conM1.iniciarOReanudar(ana.referenciaConvocatoria()));
        assertEquals(0, participacionesDe(ana.convocatoria()));
    }

    @Test
    @DisplayName("M4 caído: 503 técnico sin escrituras")
    void m4Caido() {
        autenticar(ana);
        ProgramacionParticipacionPort caido = new ProgramacionParticipacionPort() {
            @Override
            public Optional<ContextoProgramacion> consultarSesion(UUID sesion, UUID estudiante) {
                throw new ConsultaProgramacionNoDisponibleException();
            }

            @Override
            public Optional<ContextoProgramacion> consultarConvocatoria(UUID convocatoria, UUID estudiante) {
                throw new ConsultaProgramacionNoDisponibleException();
            }
        };
        AccesoProtegidoParticipacion conM4Caido = accesoCon(identidad, new TitularidadIntentosDesdeM4(jdbc, caido),
                PoliticasInicioDePrueba.limiteFijo(LIMITE_DE_PRUEBA));

        assertThrows(ConsultaProgramacionNoDisponibleException.class,
                () -> conM4Caido.iniciarOReanudar(ana.referenciaConvocatoria()));
        assertEquals(0, participacionesDe(ana.convocatoria()));
    }

    @Test
    @DisplayName("el adaptador exige la transacción del caso de uso")
    void exigeTransaccion() {
        assertThrows(IllegalTransactionStateException.class,
                () -> participaciones.bloquear(ana.referenciaConvocatoria()));
        assertThrows(IllegalTransactionStateException.class,
                () -> participaciones.crearOBloquear(ana.referenciaConvocatoria()));
        assertEquals(0, participacionesDe(ana.convocatoria()));
    }

    // --- Concurrencia -------------------------------------------------------------------

    @Test
    @DisplayName("concurrencia: solicitudes simultáneas del mismo estudiante crean una sola participación y un solo intento")
    void solicitudesSimultaneas() throws Exception {
        autenticar(ana);
        AccesoProtegidoParticipacion acceso = autorizado();
        int solicitudes = 8;
        CountDownLatch salida = new CountDownLatch(1);
        ExecutorService hilos = Executors.newFixedThreadPool(solicitudes);
        try {
            List<Future<ResultadoInicio>> futuros = new ArrayList<>();
            for (int i = 0; i < solicitudes; i++) {
                futuros.add(hilos.submit(() -> {
                    salida.await();
                    return acceso.iniciarOReanudar(ana.referenciaConvocatoria());
                }));
            }
            salida.countDown();
            List<ResultadoInicio> resultados = new ArrayList<>();
            for (Future<ResultadoInicio> futuro : futuros) {
                resultados.add(futuro.get(60, TimeUnit.SECONDS));
            }

            Set<String> intentos = resultados.stream()
                    .map(r -> r.intento().referencia().valor()).collect(Collectors.toSet());
            assertEquals(1, intentos.size(), intentos.toString());
            assertEquals(1, resultados.stream().filter(r -> !r.reanudado()).count());
            assertEquals(1, resultados.stream().map(ResultadoInicio::limiteTemporal).distinct().count());
            assertEquals(1, participacionesDe(ana.convocatoria()));
            assertEquals(1, intentosDe(ana.convocatoria()));
        } finally {
            hilos.shutdownNow();
        }
    }

    @Test
    @DisplayName("concurrencia: estudiante ajeno y titular a la vez; solo el titular obtiene intento")
    void titularYAjenoSimultaneos() throws Exception {
        IdentidadPorHilo porHilo = new IdentidadPorHilo();
        AccesoProtegidoParticipacion acceso = accesoCon(porHilo, titularidad, PoliticasInicioDePrueba.limiteFijo(LIMITE_DE_PRUEBA));
        CountDownLatch salida = new CountDownLatch(1);
        ExecutorService hilos = Executors.newFixedThreadPool(2);
        try {
            Future<ResultadoInicio> titular = hilos.submit(() -> {
                porHilo.fijar(ana);
                salida.await();
                return acceso.iniciarOReanudar(ana.referenciaConvocatoria());
            });
            Future<ResultadoInicio> ajeno = hilos.submit(() -> {
                porHilo.fijar(luis);
                salida.await();
                return acceso.iniciarOReanudar(ana.referenciaConvocatoria());
            });
            salida.countDown();

            assertFalse(titular.get(60, TimeUnit.SECONDS).reanudado());
            Exception error = assertThrows(Exception.class, () -> ajeno.get(60, TimeUnit.SECONDS));
            assertTrue(error.getCause() instanceof ConvocatoriaNoEncontradaException, String.valueOf(error.getCause()));
            assertEquals(1, intentosDe(ana.convocatoria()));
        } finally {
            hilos.shutdownNow();
        }
    }

    private Map<String, Object> filaIntento(ResultadoInicio resultado) {
        return jdbc.queryForMap("SELECT * FROM \"Intento\" WHERE \"idIntento\" = ?",
                UUID.fromString(resultado.intento().referencia().valor()));
    }

    private int participacionesDe(UUID convocatoria) {
        return jdbc.queryForObject(
                "SELECT count(*) FROM \"Participacion\" WHERE \"idConvocatoria\" = ?", Integer.class, convocatoria);
    }

    private int intentosDe(UUID convocatoria) {
        return jdbc.queryForObject("""
                SELECT count(*) FROM "Intento" i
                JOIN "Participacion" p ON p."idParticipacion" = i."idParticipacion"
                WHERE p."idConvocatoria" = ?""", Integer.class, convocatoria);
    }

    private static java.time.Instant instante(Object columna) {
        if (columna instanceof OffsetDateTime fecha) {
            return fecha.toInstant();
        }
        if (columna instanceof java.sql.Timestamp marca) {
            return marca.toInstant();
        }
        throw new IllegalStateException("Tipo de marca temporal inesperado: " + columna);
    }

    /** Identidad de prueba por hilo, para simular dos estudiantes simultáneos. */
    static final class IdentidadPorHilo implements IdentidadEstudianteActual {
        private final ThreadLocal<IdentificadorEstudiante> actual = new ThreadLocal<>();

        void fijar(CadenaIntentoPrueba cadena) {
            actual.set(new IdentificadorEstudiante(cadena.estudiante().toString()));
        }

        @Override
        public Optional<IdentificadorEstudiante> actual() {
            return Optional.ofNullable(actual.get());
        }
    }

    @SpringBootConfiguration
    @EnableAutoConfiguration
    @EnableJpaRepositories(basePackageClasses = RepositorioIntentosJpa.class)
    @EntityScan(basePackageClasses = RepositorioIntentosJpa.class)
    @Import({
        RepositorioIntentosJpa.class,
        RepositorioParticipacionesJdbc.class,
        TitularidadIntentosDesdeM4.class,
        ConfiguracionParticipacion.class
    })
    static class AppPrueba {

        @Bean
        AccesoEstudianteM4M5IT.IdentidadDePrueba identidadDePrueba() {
            return new AccesoEstudianteM4M5IT.IdentidadDePrueba();
        }

        /** Adaptador JDBC real de M4, sin activar la propiedad global del ensamblado. */
        @Bean
        ProgramacionParticipacionPort programacionParticipacion(JdbcTemplate jdbc) {
            return new ProgramacionParticipacionJdbcAdapter(jdbc);
        }
    }
}
