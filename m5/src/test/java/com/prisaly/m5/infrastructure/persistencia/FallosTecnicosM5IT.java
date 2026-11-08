package com.prisaly.m5.infrastructure.persistencia;

import static org.junit.jupiter.api.Assertions.*;

import com.prisaly.m5.domain.respuesta.Respuesta;
import com.prisaly.m5.infrastructure.FachadaParticipacionTransaccional;
import com.prisaly.m5.presentation.TraductorErroresM5;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
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

/** DC-M5M6-MACRO-02: a real PostgreSQL lock timeout (55P03) is a generic failure, writes nothing and is retryable. */
@SpringBootTest(classes = RepositorioIntentosJpaIT.AppPrueba.class)
@Testcontainers
class FallosTecnicosM5IT {
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
    @Autowired PlatformTransactionManager tx;

    @Test void tiempoDeEsperaDeBloqueoEsFalloGenericoSinEscrituraYReintentable() throws Exception {
        var cadena = CadenaIntentoPrueba.crear(jdbc);
        var retenido = new CountDownLatch(1);
        var liberar = new CountDownLatch(1);
        var retenedor = CompletableFuture.runAsync(() -> new TransactionTemplate(tx).executeWithoutResult(s -> {
            jdbc.queryForList("SELECT 1 FROM \"Intento\" WHERE \"idIntento\"=? FOR UPDATE", cadena.idIntento());
            retenido.countDown();
            try {
                assertTrue(liberar.await(30, TimeUnit.SECONDS));
            } catch (InterruptedException e) {
                throw new IllegalStateException(e);
            }
        }));
        assertTrue(retenido.await(10, TimeUnit.SECONDS));

        RuntimeException fallo = assertThrows(RuntimeException.class,
                () -> fachada.guardarRespuesta(cadena.intento(), cadena.pregunta1(), cadena.opcion1a(), 0));
        var http = TraductorErroresM5.traducir(fallo);
        assertTrue(http.isPresent(), "untranslated: " + fallo.getClass().getName());
        assertEquals(500, http.get().estado());
        assertEquals("ERROR_INTERNO", http.get().cuerpo().code());
        assertEquals(0L, jdbc.queryForObject("SELECT count(*) FROM \"Respuesta\" WHERE \"idIntento\"=?", Long.class, cadena.idIntento()));

        liberar.countDown();
        retenedor.get(30, TimeUnit.SECONDS);
        Respuesta reintento = fachada.guardarRespuesta(cadena.intento(), cadena.pregunta1(), cadena.opcion1a(), 0);
        assertEquals(1, reintento.revision());
    }
}
