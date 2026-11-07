package com.prisaly.m5.infrastructure.persistencia;

import static org.junit.jupiter.api.Assertions.*;
import com.prisaly.m4.infrastructure.adapter.ProgramacionParticipacionJdbcAdapter;
import com.prisaly.m5.application.acceso.*;
import com.prisaly.m5.application.port.RepositorioIntentos;
import com.prisaly.m5.domain.intento.MotivoFinalizacion;
import com.prisaly.m5.infrastructure.FachadaParticipacionTransaccional;
import com.prisaly.m5.infrastructure.evaluacion.FabricaConsultaIntentoEvaluacion;
import com.prisaly.m5.infrastructure.programacion.TitularidadIntentosDesdeM4;
import com.prisaly.m6.application.AccesoEvaluacionDenegadoException;
import com.prisaly.m6.application.integration.IntentoEvaluacion;
import com.prisaly.m6.application.port.AutorizacionEvaluacionPort.DecisionAcceso;
import com.prisaly.m6.application.port.ConsultaIntentoEvaluacionPort;
import com.prisaly.m6.domain.strategy.SolicitudEvaluacion;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.PlatformTransactionManager;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

/** New consumer integration; reuses the existing schema, fixture, repository and M5 services. */
@SpringBootTest(classes = RepositorioIntentosJpaIT.AppPrueba.class)
@Testcontainers
class ConsultaIntentoEvaluacionM5M6IT {
    @Container static PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:16.15");
    @DynamicPropertySource static void propiedades(DynamicPropertyRegistry r) {
        r.add("spring.datasource.url", postgres::getJdbcUrl);
        r.add("spring.datasource.username", postgres::getUsername);
        r.add("spring.datasource.password", postgres::getPassword);
        r.add("spring.jpa.hibernate.ddl-auto", () -> "validate");
        r.add("spring.jpa.properties.hibernate.globally_quoted_identifiers", () -> "true");
        r.add("spring.flyway.locations", RepositorioIntentosJpaIT::ubicacionFlywayCanonica);
    }
    @Autowired JdbcTemplate jdbc;
    @Autowired RepositorioIntentos repositorio;
    @Autowired PlatformTransactionManager tx;
    @Autowired FachadaParticipacionTransaccional fachada;
    CadenaIntentoPrueba cadena;
    @BeforeEach void crear() { cadena = CadenaIntentoPrueba.crear(jdbc); }

    ConsultaIntentoEvaluacionPort lector(UUID intento, UUID estudiante, DecisionAcceso decision) {
        var programacion = new ProgramacionParticipacionJdbcAdapter(jdbc);
        IdentidadEstudianteActual identidad = () -> Optional.of(new IdentificadorEstudiante(estudiante.toString()));
        var protegida = new AccesoProtegidoParticipacion(new ControlAccesoIntentos(identidad,
                new TitularidadIntentosDesdeM4(jdbc, programacion)), fachada);
        // Institutional authorization is a test double only, never a production bean.
        return new FabricaConsultaIntentoEvaluacion(s -> decision, protegida, repositorio,
                identidad, programacion, tx).para(new SolicitudEvaluacion(intento, UUID.randomUUID()));
    }
    @Test void entregaFinalizadoConParticipacionCasoYRespuestaReales() {
        fachada.registrarRespuesta(cadena.intento(), cadena.pregunta1(), cadena.opcion1a());
        fachada.finalizarIntento(cadena.intento(), MotivoFinalizacion.values()[0]);
        var dato = lector(cadena.idIntento(), cadena.estudiante(), DecisionAcceso.AUTORIZADO)
                .findById(cadena.idIntento()).orElseThrow();
        assertEquals(cadena.caso(), dato.idCaso());
        assertEquals(jdbc.queryForObject("SELECT \"idParticipacion\" FROM \"Intento\" WHERE \"idIntento\"=?",
                UUID.class, cadena.idIntento()), dato.idParticipacion());
        assertEquals(IntentoEvaluacion.EstadoIntento.FINALIZADO, dato.estado());
        assertEquals(UUID.fromString(cadena.pregunta1().valor()), dato.respuestas().getFirst().idPregunta());
        assertEquals(UUID.fromString(cadena.opcion1a().valor()), dato.respuestas().getFirst().idOpcionSeleccionada());
        assertThrows(UnsupportedOperationException.class, () -> dato.respuestas().clear());
    }
    @Test void conservaEnCursoYNoInventaIntentosAusentes() {
        assertEquals(IntentoEvaluacion.EstadoIntento.EN_CURSO,
                lector(cadena.idIntento(), cadena.estudiante(), DecisionAcceso.AUTORIZADO)
                        .findById(cadena.idIntento()).orElseThrow().estado());
        UUID ausente = UUID.randomUUID();
        assertTrue(lector(ausente, cadena.estudiante(), DecisionAcceso.AUTORIZADO).findById(ausente).isEmpty());
    }
    @Test void autorizacionInstitucionalNoSustituyeTitularidadPersistida() {
        var ajena = CadenaIntentoPrueba.crear(jdbc);
        assertThrows(AccesoEvaluacionDenegadoException.class,
                () -> lector(cadena.idIntento(), ajena.estudiante(), DecisionAcceso.AUTORIZADO).findById(cadena.idIntento()));
        assertThrows(AccesoEvaluacionDenegadoException.class,
                () -> lector(cadena.idIntento(), cadena.estudiante(), DecisionAcceso.DENEGADO).findById(cadena.idIntento()));
    }
}
