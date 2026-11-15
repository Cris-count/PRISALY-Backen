package com.prisaly.config;

import static org.junit.jupiter.api.Assertions.*;

import com.prisaly.m3.api.GuionNarrativoEstudiantilPort;
import com.prisaly.m3.api.GuionNarrativoNoDisponibleException;
import com.prisaly.m3.application.port.CaseAuthorizationPort;
import com.prisaly.m3.application.port.UnidadTransaccionalNarrativa;
import com.prisaly.m3.application.service.GuionNarrativoService;
import com.prisaly.m3.domain.narrativa.ValidadorGuionNarrativo;
import com.prisaly.m3.domain.rule.CasoDomainException;
import com.prisaly.m3.infrastructure.persistence.jdbc.GuionNarrativoJdbcAdapter;
import com.prisaly.m4.infrastructure.config.M4SchemaAutoConfiguration;
import com.prisaly.m4.infrastructure.config.M4SchemaContract;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

/**
 * DC-BE-SIM-INCREMENTO-A-15 on PostgreSQL 16.15 with V001-V009 + V013 + the QA-only candidate V017
 * ({@code db/sim-propuesta}, contract {@code bd0052-v013-sim-a}). The script contents are test data, not approved.
 */
@Testcontainers
class GuionNarrativoPostgreSqlIT {
    static final String GUION = """
            {"formato":"prisaly-guion-1","inicio":"entrada",
             "espacios":[{"idEspacio":"entrada","titulo":"Entrada (prueba)"}],
             "personajes":[{"idPersonaje":"guia","nombre":"Guía (prueba)"}],
             "intervenciones":[{"idIntervencion":"hola","idEspacio":"entrada","idPersonaje":"guia",
               "dialogos":[{"idPersonaje":"guia","texto":"Conversación informativa de prueba."}]}]}""";
    @Container static PostgreSQLContainer db = new PostgreSQLContainer("postgres:16.15");
    static JdbcTemplate jdbc;
    static TransactionTemplate tx;
    static GuionNarrativoJdbcAdapter adaptador;

    UUID docente, caso;

    @BeforeAll static void esquema() {
        var flyway = M4SchemaFixture.flyway(db, "classpath:db/migration", "classpath:db/sim-propuesta");
        flyway.clean(); flyway.migrate();
        jdbc = M4SchemaFixture.jdbc(db);
        tx = new TransactionTemplate(new DataSourceTransactionManager(jdbc.getDataSource()));
        adaptador = new GuionNarrativoJdbcAdapter(jdbc);
        M4SchemaAutoConfiguration.verificar(jdbc, M4SchemaContract.V013_SIM_A);
    }

    @BeforeEach void caso() {
        Integer rol = jdbc.queryForObject("INSERT INTO \"Rol\" (\"nombre\",\"descripcion\") VALUES (?, 'Fixture') RETURNING \"idRol\"",
                Integer.class, "DocSim-" + UUID.randomUUID().toString().substring(0, 8));
        docente = UUID.randomUUID();
        jdbc.update("INSERT INTO \"Usuario\" (\"idUsuario\",\"idRol\",\"nombre\",\"correo\") VALUES (?,?,'Docente SIM',?)",
                docente, rol, docente + "@prisaly.test");
        caso = UUID.randomUUID();
        jdbc.update("INSERT INTO \"Caso\" (\"idCaso\",\"idDocente\",\"nombre\",\"tiempoEstimadoSegundos\",\"estado\") VALUES (?,?,'Caso SIM',600,'Publicado')",
                caso, docente);
    }

    GuionNarrativoService servicio(UUID usuario) {
        CaseAuthorizationPort permite = new CaseAuthorizationPort() {
            public boolean canCreateCase(String u, String d) { return false; }
            public boolean canModifyCase(String u, String c) { return true; }
            public boolean canPublishCase(String u, String c) { return true; }
        };
        UnidadTransaccionalNarrativa unidad = new UnidadTransaccionalNarrativa() {
            public <T> T ejecutar(java.util.function.Supplier<T> c) { return tx.execute(e -> c.get()); }
        };
        return new GuionNarrativoService(() -> usuario, permite, adaptador, unidad, new ValidadorGuionNarrativo(Set.of()));
    }

    private void programar() {
        jdbc.update("""
                INSERT INTO "Sesion" ("idSesion","idCaso","idDocenteProgramador","nombreEspacioAcademico",
                  "inicioPrevisto","finPrevisto","tiempoMaximoParticipacionSegundos")
                VALUES (?,?,?,'Espacio SIM',now()+interval '1 day',now()+interval '1 day 2 hours',1800)""",
                UUID.randomUUID(), caso, docente);
    }

    private int filas(String estado) {
        return jdbc.queryForObject("SELECT count(*) FROM \"GuionNarrativoCaso\" WHERE \"idCaso\"=? AND \"estado\"=?", Integer.class, caso, estado);
    }

    private String sqlState(Throwable e) {
        for (Throwable t = e; t != null; t = t.getCause()) { if (t instanceof SQLException s) { return s.getSQLState(); } }
        return null;
    }

    @Test void guardarPublicarYVersionarConHuellaEstable() {
        var s = servicio(docente);
        var b1 = s.guardarBorrador(caso, GUION);
        assertEquals(1, b1.version());
        // Same document with different formatting and key order: same fingerprint.
        var b1bis = s.guardarBorrador(caso, GUION.replace("{\"idPersonaje\":\"guia\",\"nombre\":\"Guía (prueba)\"}",
                "{ \"nombre\" : \"Guía (prueba)\" , \"idPersonaje\" : \"guia\" }"));
        assertEquals(b1.idGuion(), b1bis.idGuion());
        assertEquals(b1.hash(), b1bis.hash());
        var p1 = s.publicar(caso);
        assertEquals("Publicado", p1.estado());
        assertTrue(p1.fechaPublicacion().isPresent());
        var b2 = s.guardarBorrador(caso, GUION.replace("Entrada (prueba)", "Entrada v2 (prueba)"));
        assertEquals(2, b2.version());
        assertNotEquals(p1.hash(), b2.hash());
        var p2 = s.publicar(caso);
        assertEquals(2, p2.version());
        assertEquals(1, filas("Publicado"));
        assertEquals(1, filas("Archivado"));
        assertEquals(0, filas("Borrador"));
        // The M5-facing port reads only published versions (current one by case, any non-draft by id).
        tx.executeWithoutResult(e -> {
            GuionNarrativoEstudiantilPort.GuionPublicado vigente = adaptador.publicadoDelCaso(caso).orElseThrow();
            assertEquals(p2.idGuion(), vigente.idGuion());
            assertEquals(p2.hash(), vigente.hashDefinicion());
            assertTrue(vigente.definicionJson().contains("Entrada v2 (prueba)"));
            assertEquals(1, adaptador.publicado(p1.idGuion()).orElseThrow().version());
        });
        var b3 = s.guardarBorrador(caso, GUION);
        tx.executeWithoutResult(e -> assertTrue(adaptador.publicado(b3.idGuion()).isEmpty(), "drafts are never served to M5"));
    }

    @Test void unaVezProgramadoElCasoNadaCambiaNiPorElServicioNiPorSqlDirecto() {
        var s = servicio(docente);
        s.guardarBorrador(caso, GUION);
        var publicado = s.publicar(caso);
        s.guardarBorrador(caso, GUION.replace("Entrada (prueba)", "Otra"));
        programar();
        assertNotNull(jdbc.queryForObject("SELECT \"fechaBloqueoAcademico\" FROM \"Caso\" WHERE \"idCaso\"=?", Object.class, caso));
        assertEquals("CONTENIDO_BLOQUEADO", assertThrows(CasoDomainException.class, () -> s.guardarBorrador(caso, GUION)).code());
        assertEquals("CONTENIDO_BLOQUEADO", assertThrows(CasoDomainException.class, () -> s.publicar(caso)).code());
        for (String sql : List.of(
                "UPDATE \"GuionNarrativoCaso\" SET \"estado\"='Archivado' WHERE \"idCaso\"=? AND \"estado\"='Publicado'",
                "UPDATE \"GuionNarrativoCaso\" SET \"definicion\"='{}'::jsonb WHERE \"idCaso\"=? AND \"estado\"='Borrador'",
                "DELETE FROM \"GuionNarrativoCaso\" WHERE \"idCaso\"=? AND \"estado\"='Borrador'")) {
            var error = assertThrows(DataAccessException.class, () -> jdbc.update(sql, caso));
            assertEquals("23514", sqlState(error), sql);
        }
        var nuevo = assertThrows(DataAccessException.class, () -> jdbc.update(
                "INSERT INTO \"GuionNarrativoCaso\" (\"idGuion\",\"idCaso\",\"version\",\"definicion\",\"hashDefinicion\",\"idDocenteAutor\")"
                        + " VALUES (?,?,9,'{}'::jsonb,?,?)", UUID.randomUUID(), caso, "a".repeat(64), docente));
        assertEquals("23514", sqlState(nuevo));
        tx.executeWithoutResult(e -> assertEquals(publicado.hash(), adaptador.publicadoDelCaso(caso).orElseThrow().hashDefinicion()));
    }

    @Test void unPublicadoEsInmutableAunqueElCasoNoEsteProgramado() {
        var s = servicio(docente);
        s.guardarBorrador(caso, GUION);
        var p = s.publicar(caso);
        var cambio = assertThrows(DataAccessException.class, () -> jdbc.update(
                "UPDATE \"GuionNarrativoCaso\" SET \"definicion\"='{\"x\":1}'::jsonb WHERE \"idGuion\"=?", p.idGuion()));
        assertEquals("23514", sqlState(cambio));
        var reabrir = assertThrows(DataAccessException.class, () -> jdbc.update(
                "UPDATE \"GuionNarrativoCaso\" SET \"estado\"='Borrador', \"fechaPublicacion\"=NULL WHERE \"idGuion\"=?", p.idGuion()));
        assertEquals("23514", sqlState(reabrir));
        var dosPublicados = assertThrows(DataAccessException.class, () -> jdbc.update(
                "INSERT INTO \"GuionNarrativoCaso\" (\"idGuion\",\"idCaso\",\"version\",\"estado\",\"definicion\",\"hashDefinicion\",\"idDocenteAutor\",\"fechaPublicacion\")"
                        + " VALUES (?,?,7,'Publicado','{}'::jsonb,?,?,now())", UUID.randomUUID(), caso, "b".repeat(64), docente));
        assertEquals("23505", sqlState(dosPublicados));
    }

    @Test void unaHuellaAlteradaFallaCerradoAlLeer() {
        var s = servicio(docente);
        s.guardarBorrador(caso, GUION);
        var p = s.publicar(caso);
        // Simulate tampering below the application (disable the guard only for this statement).
        jdbc.execute("ALTER TABLE \"GuionNarrativoCaso\" DISABLE TRIGGER tr_guion_bloqueo_sim");
        try {
            jdbc.update("UPDATE \"GuionNarrativoCaso\" SET \"definicion\"='{\"formato\":\"alterado\"}'::jsonb WHERE \"idGuion\"=?", p.idGuion());
        } finally {
            jdbc.execute("ALTER TABLE \"GuionNarrativoCaso\" ENABLE TRIGGER tr_guion_bloqueo_sim");
        }
        assertThrows(GuionNarrativoNoDisponibleException.class,
                () -> tx.executeWithoutResult(e -> adaptador.publicadoDelCaso(caso)));
    }

    @Test void otroDocenteNoEsAutorYElPuertoExigeTransaccion() {
        UUID ajeno = UUID.randomUUID();
        assertEquals("FORBIDDEN", assertThrows(CasoDomainException.class, () -> servicio(ajeno).guardarBorrador(caso, GUION)).code());
        assertEquals(0, filas("Borrador"));
        assertThrows(org.springframework.transaction.IllegalTransactionStateException.class,
                () -> transaccional().publicadoDelCaso(caso));
    }

    @Test void publicarYProgramarALaVezSeSerializan() throws Exception {
        var s = servicio(docente);
        s.guardarBorrador(caso, GUION);
        var pool = Executors.newFixedThreadPool(2);
        var salida = new CountDownLatch(1);
        List<Future<Object>> tareas = new ArrayList<>();
        try {
            tareas.add(pool.submit(() -> { salida.await(); try { return (Object) s.publicar(caso); } catch (RuntimeException e) { return e; } }));
            tareas.add(pool.submit(() -> { salida.await(); programar(); return "programado"; }));
            salida.countDown();
            List<Object> r = new ArrayList<>();
            for (var t : tareas) { r.add(t.get(60, TimeUnit.SECONDS)); }
            // Either the script was published before the lock, or the publication was refused after it: never a
            // script changed after the case became programmed.
            Object publicacion = r.get(0);
            if (publicacion instanceof RuntimeException e) {
                assertTrue(e instanceof CasoDomainException c && "CONTENIDO_BLOQUEADO".equals(c.code())
                        || "23514".equals(sqlState(e)), String.valueOf(e));
                assertEquals(0, filas("Publicado"));
            } else {
                assertEquals(1, filas("Publicado"));
            }
            assertEquals(Boolean.TRUE, jdbc.queryForObject("""
                    SELECT bool_and(g."fechaPublicacion" IS NULL OR g."fechaPublicacion" <= c."fechaBloqueoAcademico")
                    FROM "GuionNarrativoCaso" g JOIN "Caso" c ON c."idCaso"=g."idCaso" WHERE g."idCaso"=?""", Boolean.class, caso));
        } finally {
            pool.shutdownNow();
        }
    }

    /** The adapter wrapped as Spring would (MANDATORY propagation enforced by the transaction interceptor). */
    private GuionNarrativoEstudiantilPort transaccional() {
        var factory = new org.springframework.aop.framework.ProxyFactory(adaptador);
        var source = new org.springframework.transaction.annotation.AnnotationTransactionAttributeSource();
        factory.addAdvice(new org.springframework.transaction.interceptor.TransactionInterceptor(
                (org.springframework.transaction.TransactionManager) tx.getTransactionManager(), source));
        return (GuionNarrativoEstudiantilPort) factory.getProxy();
    }
}
