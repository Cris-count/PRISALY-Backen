package com.prisaly.m5.infrastructure.persistencia;

import static org.junit.jupiter.api.Assertions.*;

import com.prisaly.m5.application.ReferenciaIntento;
import com.prisaly.m5.application.RevisionObsoletaException;
import com.prisaly.m5.application.port.RepositorioIntentos;
import com.prisaly.m5.domain.respuesta.ReferenciaPregunta;
import com.prisaly.m5.infrastructure.FachadaParticipacionTransaccional;
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
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

/** DC-M5M6-MACRO-02: domain/persistence consistency over PostgreSQL 16.15. */
@SpringBootTest(classes = RepositorioIntentosJpaIT.AppPrueba.class)
@Testcontainers
class RobustezPersistenciaM5IT {
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
    @Autowired FachadaParticipacionTransaccional fachada;
    @Autowired RepositorioIntentos repositorio;
    @Autowired PlatformTransactionManager tx;
    CadenaIntentoPrueba cadena;

    @BeforeEach void crear() { cadena = CadenaIntentoPrueba.crear(jdbc); }

    @Test void unaPreguntaEnMayusculasNoEludeElControlDeRevision() {
        fachada.guardarRespuesta(cadena.intento(), cadena.pregunta1(), cadena.opcion1a(), 0);
        var mayusculas = new ReferenciaPregunta(cadena.pregunta1().valor().toUpperCase());
        assertThrows(RevisionObsoletaException.class,
                () -> fachada.guardarRespuesta(cadena.intento(), mayusculas, cadena.opcion1b(), 0));
        var filas = jdbc.queryForMap("SELECT \"revision\",\"idOpcionSeleccionada\" FROM \"Respuesta\" WHERE \"idIntento\"=?", cadena.idIntento());
        assertEquals(1, filas.get("revision"));
        assertEquals(UUID.fromString(cadena.opcion1a().valor()), filas.get("idOpcionSeleccionada"));
    }

    @Test void vinculoDeIntentoHuerfanoEsAusenteYNoNullPointer() {
        UUID huerfano = UUID.randomUUID();
        new TransactionTemplate(tx).executeWithoutResult(s -> {
            // Bypasses FK and triggers only in this throwaway container, to materialize corrupt data.
            jdbc.execute("SET LOCAL session_replication_role = replica");
            jdbc.update("INSERT INTO \"Intento\" (\"idIntento\",\"idParticipacion\",\"numeroIntento\",\"inicio\",\"limiteTemporal\",\"estado\") VALUES (?,?,1,now(),now()+interval '1 hour','EnCurso')",
                    huerfano, UUID.randomUUID());
        });
        var ref = new ReferenciaIntento(huerfano.toString());
        assertEquals(Optional.empty(), new TransactionTemplate(tx).execute(s -> repositorio.vinculo(ref)));
    }
}
