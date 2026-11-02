package com.prisaly.m5.infrastructure.persistencia;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.prisaly.m4.application.port.ParticipacionesPort;
import com.prisaly.m4.application.port.SesionRepository;
import com.prisaly.m4.domain.rule.ReglaNegocioException;
import com.prisaly.m4.infrastructure.adapter.ConvocatoriaRepositoryAdapter;
import com.prisaly.m4.infrastructure.adapter.SesionRepositoryAdapter;
import com.prisaly.m4.infrastructure.persistence.JpaSesionRepository;
import com.prisaly.m4.infrastructure.persistence.SesionAcademicaEntity;
import com.prisaly.m5.infrastructure.programacion.ParticipacionesM4DesdeM5;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.persistence.autoconfigure.EntityScan;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Import;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import java.util.UUID;
import java.util.function.Supplier;

/**
 * Integración del proveedor de {@link ParticipacionesPort} de M4 con PostgreSQL 16.15, V001–V009 y
 * los adaptadores JPA reales de M4. Comprueba también que, con M5 presente, M4 ya no usa su
 * respaldo que respondía 503. Requiere Docker: {@code mvnw -pl m5 -am -Pdocker-it verify}.
 */
@SpringBootTest(classes = ParticipacionesM4DesdeM5IT.AppPrueba.class)
@Testcontainers
@DisplayName("Historial de participación M4–M5 en PostgreSQL 16.15")
class ParticipacionesM4DesdeM5IT {

    @Container static PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:16.15");

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

    @Autowired private ApplicationContext contexto;

    @Autowired private ParticipacionesPort participaciones;

    @Autowired private SesionRepository sesiones;

    @Autowired private JdbcTemplate jdbc;

    @Autowired private PlatformTransactionManager transacciones;

    private CadenaIntentoPrueba cadena;

    @BeforeEach
    void crearCadena() {
        cadena = CadenaIntentoPrueba.crear(jdbc);
    }

    @Test
    @DisplayName(
            "con M5 presente, el ParticipacionesPort de M4 es el proveedor de M5 (no el respaldo"
                + " 503)")
    void proveedorDeM5Registrado() {
        assertEquals(1, contexto.getBeansOfType(ParticipacionesPort.class).size());
        assertInstanceOf(ParticipacionesM4DesdeM5.class, participaciones);
    }

    @Test
    @DisplayName(
            "una sesión con participación e intento tiene historial, también vista desde el"
                + " adaptador de M4")
    void sesionConHistorial() {
        assertTrue(
                enTransaccion(
                        () -> participaciones.tieneParticipacionesOIntentos(cadena.sesion())));
        // Ruta real de M4: SesionRepositoryAdapter con el bloqueo de la sesión, como en
        // actualizarSesion.
        assertTrue(
                enTransaccion(
                        () -> {
                            sesiones.buscarParaActualizar(cadena.sesion()).orElseThrow();
                            return sesiones.tieneParticipacionesVinculadas(cadena.sesion());
                        }));
    }

    @Test
    @DisplayName("una sesión con convocatoria pero sin participación no tiene historial")
    void convocatoriaSinParticipacion() {
        UUID sesion = nuevaSesion();
        jdbc.update(
                "INSERT INTO \"Convocatoria\" (\"idConvocatoria\", \"idSesion\", \"idEstudiante\")"
                    + " VALUES (?, ?, ?)",
                UUID.randomUUID(),
                sesion,
                cadena.estudiante());

        assertFalse(enTransaccion(() -> participaciones.tieneParticipacionesOIntentos(sesion)));
    }

    @Test
    @DisplayName("una sesión sin convocatorias o inexistente no tiene historial")
    void sesionSinConvocatorias() {
        UUID sesion = nuevaSesion();
        assertFalse(enTransaccion(() -> participaciones.tieneParticipacionesOIntentos(sesion)));
        assertFalse(
                enTransaccion(
                        () -> participaciones.tieneParticipacionesOIntentos(UUID.randomUUID())));
    }

    @Test
    @DisplayName("la participación de otra sesión no cuenta")
    void otraSesionNoCuenta() {
        CadenaIntentoPrueba otra = CadenaIntentoPrueba.crear(jdbc);
        UUID vacia = nuevaSesion();

        assertTrue(
                enTransaccion(() -> participaciones.tieneParticipacionesOIntentos(otra.sesion())));
        assertFalse(enTransaccion(() -> participaciones.tieneParticipacionesOIntentos(vacia)));
    }

    @Test
    @DisplayName("un fallo técnico de la base es una excepción, nunca false")
    void falloTecnicoNoEsFalse() {
        jdbc.execute("ALTER TABLE public.\"Participacion\" RENAME TO \"ParticipacionOcultaM5IT\"");
        try {
            var error =
                    assertThrows(
                            ReglaNegocioException.class,
                            () ->
                                    enTransaccion(
                                            () ->
                                                    participaciones.tieneParticipacionesOIntentos(
                                                            cadena.sesion())));
            assertEquals("M4-PROVEEDOR-NO-DISPONIBLE", error.getCodigo());
            assertInstanceOf(PersistenciaIntentoException.class, error.getCause());
        } finally {
            jdbc.execute(
                    "ALTER TABLE public.\"ParticipacionOcultaM5IT\" RENAME TO \"Participacion\"");
        }
    }

    @Test
    @DisplayName("exige la transacción de M4 que modifica la sesión")
    void exigeTransaccion() {
        assertThrows(
                IllegalStateException.class,
                () -> participaciones.tieneParticipacionesOIntentos(cadena.sesion()));
    }

    @Test
    void rechazaTransaccionDeSoloLectura() {
        var lectura = new TransactionTemplate(transacciones);
        lectura.setReadOnly(true);
        lectura.executeWithoutResult(
                tx ->
                        assertThrows(
                                IllegalStateException.class,
                                () ->
                                        participaciones.tieneParticipacionesOIntentos(
                                                cadena.sesion())));
    }

    /** Sesión adicional sobre el caso publicado de la cadena, sin convocatorias. */
    private UUID nuevaSesion() {
        UUID sesion = UUID.randomUUID();
        jdbc.update(
                "INSERT INTO \"Sesion\" (\"idSesion\", \"idCaso\", \"idDocenteProgramador\","
                    + " \"nombreEspacioAcademico\", \"inicioPrevisto\", \"finPrevisto\","
                    + " \"tiempoMaximoParticipacionSegundos\") VALUES (?, ?, ?, 'Sesión adicional"
                    + " M5IT', now() + interval '1 day', now() + interval '2 days', 1800)",
                sesion,
                cadena.caso(),
                cadena.docente());
        return sesion;
    }

    private boolean enTransaccion(Supplier<Boolean> operacion) {
        return Boolean.TRUE.equals(
                new TransactionTemplate(transacciones).execute(estado -> operacion.get()));
    }

    @SpringBootConfiguration
    @EnableAutoConfiguration
    @EnableJpaRepositories(
            basePackageClasses = {RepositorioIntentosJpa.class, JpaSesionRepository.class})
    @EntityScan(basePackageClasses = {RepositorioIntentosJpa.class, SesionAcademicaEntity.class})
    @Import({
        HistorialParticipacionJdbc.class,
        ParticipacionesM4DesdeM5.class,
        ConvocatoriaRepositoryAdapter.class,
        SesionRepositoryAdapter.class
    })
    static class AppPrueba {}
}
