package com.prisaly.m5.infrastructure.persistencia;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.persistence.autoconfigure.EntityScan;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.IllegalTransactionStateException;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import com.prisaly.m5.application.IntentoNoEncontradoException;
import com.prisaly.m5.application.ReferenciaIntento;
import com.prisaly.m5.application.ResumenIntento;
import com.prisaly.m5.application.port.RepositorioIntentos;
import com.prisaly.m5.domain.intento.EstadoIntento;
import com.prisaly.m5.domain.intento.Intento;
import com.prisaly.m5.domain.intento.IntentoFinalizadoException;
import com.prisaly.m5.domain.intento.MotivoFinalizacion;
import com.prisaly.m5.domain.intento.RespuestaDuplicadaException;
import com.prisaly.m5.domain.intento.TransicionIntentoInvalidaException;
import com.prisaly.m5.domain.respuesta.Respuesta;
import com.prisaly.m5.infrastructure.ConfiguracionParticipacion;
import com.prisaly.m5.infrastructure.FachadaParticipacionTransaccional;

/**
 * Integración de la persistencia de M5 con PostgreSQL 16.15 real y las
 * migraciones canónicas V001–V009 aplicadas por Flyway. Requiere Docker:
 * {@code mvnw -pl m5 -am -Pdocker-it verify}.
 */
@SpringBootTest(classes = RepositorioIntentosJpaIT.AppPrueba.class)
@Testcontainers
@DisplayName("Persistencia de intentos en PostgreSQL 16.15")
class RepositorioIntentosJpaIT {

    @Container
    static PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:16.15");

    @DynamicPropertySource
    static void propiedades(DynamicPropertyRegistry registro) {
        registro.add("spring.datasource.url", postgres::getJdbcUrl);
        registro.add("spring.datasource.username", postgres::getUsername);
        registro.add("spring.datasource.password", postgres::getPassword);
        registro.add("spring.jpa.hibernate.ddl-auto", () -> "validate");
        registro.add("spring.jpa.open-in-view", () -> "false");
        registro.add("spring.jpa.properties.hibernate.globally_quoted_identifiers", () -> "true");
        registro.add("spring.flyway.enabled", () -> "true");
        registro.add("spring.flyway.locations", RepositorioIntentosJpaIT::ubicacionFlywayCanonica);
    }

    @Autowired
    private FachadaParticipacionTransaccional fachada;

    @Autowired
    private RepositorioIntentos repositorio;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private PlatformTransactionManager transacciones;

    private CadenaIntentoPrueba cadena;
    private ExecutorService hilos;

    @BeforeEach
    void crearCadena() {
        cadena = CadenaIntentoPrueba.crear(jdbc);
        hilos = Executors.newFixedThreadPool(2);
    }

    @AfterEach
    void cerrarHilos() {
        hilos.shutdownNow();
    }

    @Test
    @DisplayName("consulta un intento persistido EnCurso sin respuestas")
    void consultaIntentoPersistido() {
        ResumenIntento resumen = fachada.consultarIntento(cadena.intento());

        assertEquals(EstadoIntento.EN_CURSO, resumen.estado());
        assertEquals(List.of(), resumen.respuestas());
    }

    @Test
    @DisplayName("informa intentos inexistentes, incluidas referencias que no son uuid")
    void informaIntentoInexistente() {
        assertThrows(IntentoNoEncontradoException.class,
                () -> fachada.consultarIntento(new ReferenciaIntento(UUID.randomUUID().toString())));
        assertThrows(IntentoNoEncontradoException.class,
                () -> fachada.consultarIntento(new ReferenciaIntento("no-es-uuid")));
    }

    @Test
    @DisplayName("persiste y recupera una respuesta con revisión 1")
    void persisteYRecuperaRespuesta() {
        fachada.registrarRespuesta(cadena.intento(), cadena.pregunta1(), cadena.opcion1a());

        assertEquals(List.of(new Respuesta(cadena.pregunta1(), cadena.opcion1a())),
                fachada.consultarIntento(cadena.intento()).respuestas());
        Map<String, Object> fila = filaRespuesta(cadena.pregunta1().valor());
        assertEquals(UUID.fromString(cadena.opcion1a().valor()), fila.get("idOpcionSeleccionada"));
        assertEquals(1, fila.get("revision"));
    }

    @Test
    @DisplayName("la actualización provisional reemplaza la opción en la misma fila e incrementa la revisión")
    void actualizacionIncrementaRevision() {
        fachada.registrarRespuesta(cadena.intento(), cadena.pregunta1(), cadena.opcion1a());
        fachada.actualizarRespuesta(cadena.intento(), cadena.pregunta1(), cadena.opcion1b());

        Map<String, Object> fila = filaRespuesta(cadena.pregunta1().valor());
        assertEquals(UUID.fromString(cadena.opcion1b().valor()), fila.get("idOpcionSeleccionada"));
        assertEquals(2, fila.get("revision"));
        assertEquals(1, contarRespuestas());
    }

    @Test
    @DisplayName("persiste la finalización con estado, motivo y fin")
    void persisteFinalizacion() {
        fachada.registrarRespuesta(cadena.intento(), cadena.pregunta1(), cadena.opcion1a());
        fachada.finalizarIntento(cadena.intento(), MotivoFinalizacion.TIEMPO_VENCIDO);

        Map<String, Object> fila = jdbc.queryForMap(
                "SELECT \"estado\", \"motivoFinalizacion\", \"fin\" FROM \"Intento\" WHERE \"idIntento\" = ?",
                cadena.idIntento());
        assertEquals("Finalizado", fila.get("estado"));
        assertEquals("TIEMPO_VENCIDO", fila.get("motivoFinalizacion"));
        assertNotNull(fila.get("fin"));

        ResumenIntento recuperado = fachada.consultarIntento(cadena.intento());
        assertEquals(Optional.of(MotivoFinalizacion.TIEMPO_VENCIDO), recuperado.motivoFinalizacion());
        assertEquals(1, recuperado.respuestas().size());
    }

    @Test
    @DisplayName("rechaza respuestas en un intento finalizado sin escribir en la base")
    void rechazaRespuestaEnIntentoFinalizado() {
        fachada.finalizarIntento(cadena.intento(), MotivoFinalizacion.ENTREGA_ESTUDIANTE);

        assertThrows(IntentoFinalizadoException.class,
                () -> fachada.registrarRespuesta(cadena.intento(), cadena.pregunta1(), cadena.opcion1a()));
        assertEquals(0, contarRespuestas());
    }

    @Test
    @DisplayName("la base impone una respuesta por intento y pregunta")
    void unicidadPorIntentoYPregunta() {
        fachada.registrarRespuesta(cadena.intento(), cadena.pregunta1(), cadena.opcion1a());

        assertThrows(DataIntegrityViolationException.class, () -> jdbc.update(
                "INSERT INTO \"Respuesta\" (\"idRespuesta\", \"idIntento\", \"idPregunta\", \"idOpcionSeleccionada\")"
                        + " VALUES (?, ?, ?, ?)",
                UUID.randomUUID(), cadena.idIntento(),
                UUID.fromString(cadena.pregunta1().valor()), UUID.fromString(cadena.opcion1b().valor())));
        assertEquals(1, contarRespuestas());
    }

    @Test
    @DisplayName("una opción de otra pregunta viola la FK compuesta y no deja escrituras parciales")
    void opcionDeOtraPreguntaSeRevierte() {
        fachada.registrarRespuesta(cadena.intento(), cadena.pregunta2(), cadena.opcion2a());

        assertThrows(DataAccessException.class,
                () -> fachada.registrarRespuesta(cadena.intento(), cadena.pregunta1(), cadena.opcion2a()));

        assertEquals(1, contarRespuestas());
        assertEquals(EstadoIntento.EN_CURSO, fachada.consultarIntento(cadena.intento()).estado());
    }

    @Test
    @DisplayName("un fallo posterior al guardado revierte toda la transacción")
    void rollbackDeOperacionFallida() {
        TransactionTemplate plantilla = new TransactionTemplate(transacciones);

        assertThrows(IllegalStateException.class, () -> plantilla.executeWithoutResult(estado -> {
            Intento intento = repositorio.buscar(cadena.intento()).orElseThrow();
            intento.registrarRespuesta(cadena.pregunta1(), cadena.opcion1a());
            intento.finalizar(MotivoFinalizacion.ENTREGA_ESTUDIANTE);
            repositorio.guardar(cadena.intento(), intento);
            throw new IllegalStateException("fallo simulado después de guardar");
        }));

        assertEquals(0, contarRespuestas());
        assertEquals(EstadoIntento.EN_CURSO, fachada.consultarIntento(cadena.intento()).estado());
    }

    @Test
    @DisplayName("el adaptador exige una transacción abierta por el caso de uso")
    void exigeTransaccion() {
        assertThrows(IllegalTransactionStateException.class, () -> repositorio.buscar(cadena.intento()));
    }

    @Test
    @DisplayName("concurrencia: una finalización espera a que termine un registro en curso y lo conserva")
    void finalizacionEsperaAlRegistro() throws Exception {
        CountDownLatch bloqueado = new CountDownLatch(1);
        Future<?> registro = hilos.submit(() -> new TransactionTemplate(transacciones).executeWithoutResult(estado -> {
            Intento intento = repositorio.buscar(cadena.intento()).orElseThrow();
            bloqueado.countDown();
            dormir(800);
            intento.registrarRespuesta(cadena.pregunta1(), cadena.opcion1a());
            repositorio.guardar(cadena.intento(), intento);
        }));
        assertTrue(bloqueado.await(10, TimeUnit.SECONDS));

        long inicio = System.nanoTime();
        Future<ResumenIntento> finalizacion = hilos.submit(
                () -> fachada.finalizarIntento(cadena.intento(), MotivoFinalizacion.TIEMPO_VENCIDO));
        ResumenIntento resultado = finalizacion.get(20, TimeUnit.SECONDS);
        long esperaMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - inicio);
        registro.get(20, TimeUnit.SECONDS);

        assertTrue(esperaMs >= 500, "la finalización debió esperar el bloqueo; esperó " + esperaMs + " ms");
        assertEquals(EstadoIntento.FINALIZADO, resultado.estado());
        assertEquals(List.of(new Respuesta(cadena.pregunta1(), cadena.opcion1a())), resultado.respuestas());
        assertEquals(1, contarRespuestas());
    }

    @Test
    @DisplayName("concurrencia: un registro que espera a una finalización en curso es rechazado")
    void registroEsperaAFinalizacionYEsRechazado() throws Exception {
        CountDownLatch bloqueado = new CountDownLatch(1);
        Future<?> finalizacion = hilos.submit(() -> new TransactionTemplate(transacciones).executeWithoutResult(estado -> {
            Intento intento = repositorio.buscar(cadena.intento()).orElseThrow();
            bloqueado.countDown();
            dormir(800);
            intento.finalizar(MotivoFinalizacion.TIEMPO_VENCIDO);
            repositorio.guardar(cadena.intento(), intento);
        }));
        assertTrue(bloqueado.await(10, TimeUnit.SECONDS));

        Future<ResumenIntento> registro = hilos.submit(
                () -> fachada.registrarRespuesta(cadena.intento(), cadena.pregunta1(), cadena.opcion1a()));
        ExecutionException error = assertThrows(ExecutionException.class, () -> registro.get(20, TimeUnit.SECONDS));
        finalizacion.get(20, TimeUnit.SECONDS);

        assertTrue(error.getCause() instanceof IntentoFinalizadoException, String.valueOf(error.getCause()));
        assertEquals(0, contarRespuestas());
    }

    @Test
    @DisplayName("una finalización repetida y rechazada no altera estado, motivo ni la marca fin")
    void finalizacionRepetidaSinEfectos() {
        fachada.finalizarIntento(cadena.intento(), MotivoFinalizacion.ENTREGA_ESTUDIANTE);
        Map<String, Object> antes = filaIntento();

        assertThrows(TransicionIntentoInvalidaException.class,
                () -> fachada.finalizarIntento(cadena.intento(), MotivoFinalizacion.TIEMPO_VENCIDO));
        assertThrows(TransicionIntentoInvalidaException.class,
                () -> fachada.finalizarIntento(cadena.intento(), MotivoFinalizacion.ENTREGA_ESTUDIANTE));

        assertEquals(antes, filaIntento());
    }

    @Test
    @DisplayName("concurrencia: dos finalizaciones simultáneas; solo una tiene efecto")
    void finalizacionesSimultaneas() throws Exception {
        List<Object> resultados = carrera(
                () -> fachada.finalizarIntento(cadena.intento(), MotivoFinalizacion.TIEMPO_VENCIDO),
                () -> fachada.finalizarIntento(cadena.intento(), MotivoFinalizacion.ENTREGA_ESTUDIANTE));

        assertEquals(1, resultados.stream().filter(ResumenIntento.class::isInstance).count(), resultados.toString());
        assertEquals(1, resultados.stream().filter(TransicionIntentoInvalidaException.class::isInstance).count(),
                resultados.toString());
        ResumenIntento ganador = (ResumenIntento) resultados.stream()
                .filter(ResumenIntento.class::isInstance).findFirst().orElseThrow();
        assertEquals(ganador.motivoFinalizacion().orElseThrow().name(), filaIntento().get("motivoFinalizacion"));
    }

    @Test
    @DisplayName("concurrencia: respuestas simultáneas a preguntas distintas se conservan ambas")
    void respuestasSimultaneasDistintas() throws Exception {
        List<Object> resultados = carrera(
                () -> fachada.registrarRespuesta(cadena.intento(), cadena.pregunta1(), cadena.opcion1a()),
                () -> fachada.registrarRespuesta(cadena.intento(), cadena.pregunta2(), cadena.opcion2a()));

        assertTrue(resultados.stream().allMatch(ResumenIntento.class::isInstance), resultados.toString());
        assertEquals(2, contarRespuestas());
        assertEquals(2, fachada.consultarIntento(cadena.intento()).respuestas().size());
    }

    @Test
    @DisplayName("concurrencia: dos registros simultáneos de la misma pregunta dejan una sola respuesta")
    void respuestasSimultaneasMismaPregunta() throws Exception {
        List<Object> resultados = carrera(
                () -> fachada.registrarRespuesta(cadena.intento(), cadena.pregunta1(), cadena.opcion1a()),
                () -> fachada.registrarRespuesta(cadena.intento(), cadena.pregunta1(), cadena.opcion1b()));

        assertEquals(1, resultados.stream().filter(ResumenIntento.class::isInstance).count(), resultados.toString());
        assertEquals(1, resultados.stream().filter(RespuestaDuplicadaException.class::isInstance).count(),
                resultados.toString());
        assertEquals(1, contarRespuestas());
        assertEquals(1, filaRespuesta(cadena.pregunta1().valor()).get("revision"));
    }

    /**
     * Lanza dos operaciones a la vez y devuelve, para cada una, su resultado
     * o la excepción que produjo.
     */
    private List<Object> carrera(Callable<ResumenIntento> primera, Callable<ResumenIntento> segunda) throws Exception {
        CountDownLatch salida = new CountDownLatch(1);
        List<Future<Object>> futuros = List.of(primera, segunda).stream()
                .map(operacion -> hilos.submit(() -> {
                    salida.await();
                    try {
                        return (Object) operacion.call();
                    } catch (RuntimeException e) {
                        return e;
                    }
                }))
                .toList();
        salida.countDown();
        List<Object> resultados = new ArrayList<>();
        for (Future<Object> futuro : futuros) {
            resultados.add(futuro.get(20, TimeUnit.SECONDS));
        }
        return resultados;
    }

    private Map<String, Object> filaIntento() {
        return jdbc.queryForMap(
                "SELECT \"estado\", \"motivoFinalizacion\", \"fin\" FROM \"Intento\" WHERE \"idIntento\" = ?",
                cadena.idIntento());
    }

    private int contarRespuestas() {
        return jdbc.queryForObject("SELECT count(*) FROM \"Respuesta\" WHERE \"idIntento\" = ?",
                Integer.class, cadena.idIntento());
    }

    private Map<String, Object> filaRespuesta(String idPregunta) {
        return jdbc.queryForMap(
                "SELECT \"idOpcionSeleccionada\", \"revision\" FROM \"Respuesta\" WHERE \"idIntento\" = ? AND \"idPregunta\" = ?",
                cadena.idIntento(), UUID.fromString(idPregunta));
    }

    private static void dormir(long milisegundos) {
        try {
            Thread.sleep(milisegundos);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(e);
        }
    }

    static String ubicacionFlywayCanonica() {
        Path actual = Path.of(System.getProperty("user.dir")).toAbsolutePath().normalize();
        for (Path candidata : List.of(
                actual.resolve("app/src/main/resources/db/migration"),
                actual.resolve("../app/src/main/resources/db/migration").normalize())) {
            if (Files.exists(candidata.resolve("V001__bd0052_estructura.sql"))) {
                return "filesystem:" + candidata;
            }
        }
        throw new IllegalStateException("No se encontraron las migraciones V001–V009 desde user.dir=" + actual);
    }

    @SpringBootConfiguration
    @EnableAutoConfiguration
    @EnableJpaRepositories(basePackageClasses = RepositorioIntentosJpa.class)
    @EntityScan(basePackageClasses = RepositorioIntentosJpa.class)
    @Import({RepositorioIntentosJpa.class, ConfiguracionParticipacion.class})
    static class AppPrueba {
    }
}
