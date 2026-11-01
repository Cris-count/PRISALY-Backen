package com.prisaly.m5.infrastructure.persistencia;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

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

import com.prisaly.m1.application.service.DenyingStudentAccessService;
import com.prisaly.m4.api.ConsultaProgramacionNoDisponibleException;
import com.prisaly.m4.api.ProgramacionParticipacionPort;
import com.prisaly.m4.infrastructure.adapter.ProgramacionParticipacionJdbcAdapter;
import com.prisaly.m5.api.HistorialParticipacionPort;
import com.prisaly.m5.application.IntentoNoEncontradoException;
import com.prisaly.m5.application.ReferenciaIntento;
import com.prisaly.m5.application.acceso.AccesoIntentoDenegadoException;
import com.prisaly.m5.application.acceso.AccesoProtegidoParticipacion;
import com.prisaly.m5.application.acceso.ControlAccesoIntentos;
import com.prisaly.m5.application.acceso.EstudianteNoIdentificadoException;
import com.prisaly.m5.application.acceso.IdentidadEstudianteActual;
import com.prisaly.m5.application.acceso.IdentificadorEstudiante;
import com.prisaly.m5.application.acceso.TitularidadIntentos;
import com.prisaly.m5.domain.intento.EstadoIntento;
import com.prisaly.m5.domain.intento.MotivoFinalizacion;
import com.prisaly.m5.infrastructure.ConfiguracionAccesoEstudiante;
import com.prisaly.m5.infrastructure.ConfiguracionParticipacion;
import com.prisaly.m5.infrastructure.FachadaParticipacionTransaccional;
import com.prisaly.m5.infrastructure.identidad.IdentidadEstudianteDesdeM1;
import com.prisaly.m5.infrastructure.programacion.TitularidadIntentosDesdeM4;
import com.prisaly.m5.presentation.TraductorErroresM5;

/**
 * Integración M1–M4–M5 sobre PostgreSQL 16.15 y V001–V009: el proxy de
 * protección con la titularidad real de M4 ({@link ProgramacionParticipacionJdbcAdapter})
 * y el historial de participación que M5 publica para M4.
 *
 * <p>La identidad estudiantil es un doble de prueba ({@link IdentidadDePrueba}),
 * porque DD-02 sigue abierta; el caso con el proveedor real de M1 usa
 * {@link DenyingStudentAccessService} y debe denegar. Requiere Docker:
 * {@code mvnw -pl m5 -am -Pdocker-it verify}.
 */
@SpringBootTest(classes = AccesoEstudianteM4M5IT.AppPrueba.class)
@Testcontainers
@DisplayName("Acceso estudiantil M1–M4–M5 en PostgreSQL 16.15")
class AccesoEstudianteM4M5IT {

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

    @Autowired private AccesoProtegidoParticipacion acceso;

    @Autowired private IdentidadDePrueba identidad;

    @Autowired private TitularidadIntentos titularidad;

    @Autowired private FachadaParticipacionTransaccional fachada;

    @Autowired private HistorialParticipacionPort historial;

    @Autowired private JdbcTemplate jdbc;

    @Autowired private PlatformTransactionManager transacciones;

    private CadenaIntentoPrueba ana;
    private CadenaIntentoPrueba luis;

    @BeforeEach
    void crearCadenas() {
        ana = CadenaIntentoPrueba.crear(jdbc);
        luis = CadenaIntentoPrueba.crear(jdbc);
        identidad.actual = Optional.empty();
    }

    private void autenticar(CadenaIntentoPrueba cadena) {
        identidad.actual = Optional.of(new IdentificadorEstudiante(cadena.estudiante().toString()));
    }

    @Test
    @DisplayName("el titular consulta, responde y finaliza su intento a través del proxy")
    void titularOperaSuIntento() {
        autenticar(ana);

        acceso.registrarRespuesta(ana.intento(), ana.pregunta1(), ana.opcion1a());
        acceso.finalizarIntento(ana.intento(), MotivoFinalizacion.ENTREGA_ESTUDIANTE);

        assertEquals(EstadoIntento.FINALIZADO, acceso.consultarIntento(ana.intento()).estado());
        assertEquals(1, respuestasDe(ana));
    }

    @Test
    @DisplayName("suplantación: otro estudiante con el id del intento no lo consulta ni lo modifica")
    void otroEstudianteNoAccede() {
        autenticar(luis);

        assertThrows(AccesoIntentoDenegadoException.class, () -> acceso.consultarIntento(ana.intento()));
        assertThrows(AccesoIntentoDenegadoException.class,
                () -> acceso.registrarRespuesta(ana.intento(), ana.pregunta1(), ana.opcion1a()));
        assertThrows(AccesoIntentoDenegadoException.class,
                () -> acceso.finalizarIntento(ana.intento(), MotivoFinalizacion.ENTREGA_ESTUDIANTE));

        assertEquals(0, respuestasDe(ana));
        assertEquals("EnCurso", estadoDe(ana));
    }

    @Test
    @DisplayName("un estudiante inexistente en BD-005.2 no es titular de ningún intento")
    void estudianteInexistente() {
        identidad.actual = Optional.of(new IdentificadorEstudiante(UUID.randomUUID().toString()));

        assertThrows(AccesoIntentoDenegadoException.class, () -> acceso.consultarIntento(ana.intento()));
    }

    @Test
    @DisplayName("un intento inexistente se informa como no encontrado")
    void intentoInexistente() {
        autenticar(ana);

        assertThrows(IntentoNoEncontradoException.class,
                () -> acceso.consultarIntento(new ReferenciaIntento(UUID.randomUUID().toString())));
        assertThrows(IntentoNoEncontradoException.class,
                () -> acceso.consultarIntento(new ReferenciaIntento("no-es-uuid")));
    }

    @Test
    @DisplayName("sin identidad no se lee ni se escribe nada")
    void sinIdentidad() {
        assertThrows(EstudianteNoIdentificadoException.class,
                () -> acceso.registrarRespuesta(ana.intento(), ana.pregunta1(), ana.opcion1a()));
        assertEquals(0, respuestasDe(ana));
    }

    @Test
    @DisplayName("DD-02 abierta: con el proveedor real de M1 el titular tampoco accede")
    void proveedorRealDeM1Deniega() {
        AccesoProtegidoParticipacion conM1 = new AccesoProtegidoParticipacion(
                new ControlAccesoIntentos(new IdentidadEstudianteDesdeM1(new DenyingStudentAccessService()), titularidad),
                fachada);

        assertThrows(EstudianteNoIdentificadoException.class, () -> conM1.consultarIntento(ana.intento()));
    }

    @Test
    @DisplayName("M4 indisponible: 503 sin la causa SQL y sin escrituras")
    void m4Indisponible() {
        autenticar(ana);
        jdbc.execute("ALTER TABLE public.\"Sesion\" RENAME TO \"SesionOcultaM5IT\"");
        try {
            ConsultaProgramacionNoDisponibleException error = assertThrows(
                    ConsultaProgramacionNoDisponibleException.class,
                    () -> acceso.registrarRespuesta(ana.intento(), ana.pregunta1(), ana.opcion1a()));

            TraductorErroresM5.ErrorHttp http = TraductorErroresM5.traducir(error).orElseThrow();
            assertEquals(503, http.estado());
            assertFalse(http.cuerpo().message().contains("Sesion"));
            assertFalse(http.cuerpo().message().contains("SELECT"));
        } finally {
            jdbc.execute("ALTER TABLE public.\"SesionOcultaM5IT\" RENAME TO \"Sesion\"");
        }
        assertEquals(0, respuestasDe(ana));
    }

    @Test
    @DisplayName("historial para M4: refleja participaciones de M5 solo de las convocatorias pedidas")
    void historialDeParticipacion() {
        assertTrue(existeEnTransaccion(List.of(ana.convocatoria())));
        assertTrue(existeEnTransaccion(Set.of(UUID.randomUUID(), luis.convocatoria())));
        assertFalse(existeEnTransaccion(List.of(UUID.randomUUID())));
        assertFalse(existeEnTransaccion(List.of()));
    }

    private boolean existeEnTransaccion(Collection<UUID> convocatorias) {
        Boolean existe = new TransactionTemplate(transacciones)
                .execute(estado -> historial.existeParticipacion(convocatorias));
        return Boolean.TRUE.equals(existe);
    }

    @Test
    @DisplayName("historial para M4: exige la transacción del llamador")
    void historialExigeTransaccion() {
        assertThrows(IllegalTransactionStateException.class,
                () -> historial.existeParticipacion(List.of(ana.convocatoria())));
    }

    private int respuestasDe(CadenaIntentoPrueba cadena) {
        return jdbc.queryForObject(
                "SELECT count(*) FROM \"Respuesta\" WHERE \"idIntento\" = ?", Integer.class, cadena.idIntento());
    }

    private String estadoDe(CadenaIntentoPrueba cadena) {
        return jdbc.queryForObject(
                "SELECT \"estado\" FROM \"Intento\" WHERE \"idIntento\" = ?", String.class, cadena.idIntento());
    }

    /** Identidad estudiantil de prueba: sustituye a M1 mientras DD-02 siga abierta. */
    static final class IdentidadDePrueba implements IdentidadEstudianteActual {
        volatile Optional<IdentificadorEstudiante> actual = Optional.empty();

        @Override
        public Optional<IdentificadorEstudiante> actual() {
            return actual;
        }
    }

    @SpringBootConfiguration
    @EnableAutoConfiguration
    @EnableJpaRepositories(basePackageClasses = RepositorioIntentosJpa.class)
    @EntityScan(basePackageClasses = RepositorioIntentosJpa.class)
    @Import({
        RepositorioIntentosJpa.class,
        HistorialParticipacionJdbc.class,
        TitularidadIntentosDesdeM4.class,
        ConfiguracionParticipacion.class,
        ConfiguracionAccesoEstudiante.class
    })
    static class AppPrueba {

        @Bean
        IdentidadDePrueba identidadDePrueba() {
            return new IdentidadDePrueba();
        }

        /** Adaptador JDBC real de M4, sin activar la propiedad global del ensamblado. */
        @Bean
        ProgramacionParticipacionPort programacionParticipacion(JdbcTemplate jdbc) {
            return new ProgramacionParticipacionJdbcAdapter(jdbc);
        }
    }
}
