package com.prisaly.m4.infrastructure;

import static org.junit.jupiter.api.Assertions.*;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.UUID;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.testcontainers.postgresql.PostgreSQLContainer;

/**
 * Diagnóstico de la línea base SQL compartida en un contenedor desechable.
 * NO es integración con los servicios M1/M3/M5 ni usa el volumen oficial.
 * Requiere snapshot V001-V009 verificado y pasado mediante propiedad explícita.
 */
@EnabledIfSystemProperty(named = "m4.baseline.migrations", matches = ".+")
class Bd0052BaselineIT {
    private static final PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:16.15");
    private UUID docente;

    @BeforeAll
    static void iniciar() {
        postgres.start();
    }

    @AfterAll
    static void terminar() {
        if (postgres.isRunning()) {
            System.out.println("BD0052 servidor SQL:\n" + postgres.getLogs());
        }
        postgres.stop();
    }

    private Flyway migraciones(String destino) {
        return Flyway.configure().dataSource(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword())
                .locations("filesystem:" + System.getProperty("m4.baseline.migrations"), "classpath:db/migration")
                .target(destino).cleanDisabled(false).load();
    }

    private Connection conexion() throws SQLException {
        return DriverManager.getConnection(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword());
    }

    @BeforeEach
    void restaurarSoloContenedorEfimero() throws SQLException {
        var flyway = migraciones("9");
        flyway.clean();
        flyway.migrate();
        flyway.validate();
        docente = UUID.randomUUID();
        try (var connection = conexion(); var sql = connection.createStatement()) {
            sql.executeUpdate("INSERT INTO \"Rol\" (\"idRol\",\"nombre\",\"descripcion\") VALUES (1,'Docente','Fixture SQL')");
            sql.executeUpdate("INSERT INTO \"Usuario\" (\"idUsuario\",\"idRol\",\"nombre\",\"correo\") VALUES ('"
                    + docente + "',1,'Fixture','fixture@example.invalid')");
        }
    }

    private UUID caso(String estado) throws SQLException {
        UUID id = UUID.randomUUID();
        try (var connection = conexion(); var sql = connection.prepareStatement(
                "INSERT INTO \"Caso\" (\"idCaso\",\"idDocente\",\"nombre\",\"tiempoEstimadoSegundos\",\"estado\") VALUES (?,?,'Caso SQL',60,?)")) {
            sql.setObject(1, id);
            sql.setObject(2, docente);
            sql.setString(3, estado);
            sql.executeUpdate();
        }
        return id;
    }

    private UUID programar(Connection connection, UUID caso) throws SQLException {
        UUID sesion = UUID.randomUUID();
        try (var sql = connection.prepareStatement(
                "INSERT INTO \"Sesion\" (\"idSesion\",\"idCaso\",\"idDocenteProgramador\",\"nombreEspacioAcademico\","
                + "\"inicioPrevisto\",\"finPrevisto\",\"tiempoMaximoParticipacionSegundos\") "
                + "VALUES (?,?,?,'Espacio SQL',now()+interval '1 day',now()+interval '2 days',60)")) {
            sql.setObject(1, sesion);
            sql.setObject(2, caso);
            sql.setObject(3, docente);
            sql.executeUpdate();
        }
        return sesion;
    }

    @Test
    void rf09RealDeBaseDatosRechazaBorrador() throws SQLException {
        UUID borrador = caso("Borrador");
        try (var connection = conexion()) {
            var error = assertThrows(SQLException.class, () -> programar(connection, borrador));
            assertEquals("23514", error.getSQLState());
            assertEquals("tr_sesion_rf09_caso_programable",
                    ((org.postgresql.util.PSQLException) error).getServerErrorMessage().getConstraint());
            try (var sql = connection.createStatement(); var resultado = sql.executeQuery("SELECT count(*) FROM \"Sesion\"")) {
                assertTrue(resultado.next());
                assertEquals(0, resultado.getInt(1));
            }
        }
        System.out.println("BD0052 RF09 SQL real V009: Borrador rechazado, SQLSTATE 23514; sesiones=0. Sin servicio M3.");
    }

    @Test
    void publicadoProgramaYBloqueaContenidoConTriggerReal() throws SQLException {
        UUID publicado = caso("Publicado");
        try (var connection = conexion()) {
            programar(connection, publicado);
            try (var sql = connection.prepareStatement("SELECT \"fechaBloqueoAcademico\" FROM \"Caso\" WHERE \"idCaso\"=?")) {
                sql.setObject(1, publicado);
                try (var resultado = sql.executeQuery()) {
                    assertTrue(resultado.next());
                    assertNotNull(resultado.getObject(1));
                }
            }
            try (var sql = connection.prepareStatement("UPDATE \"Caso\" SET \"nombre\"='Alterado' WHERE \"idCaso\"=?")) {
                sql.setObject(1, publicado);
                var error = assertThrows(SQLException.class, sql::executeUpdate);
                assertEquals("23514", error.getSQLState());
                assertEquals("tr_caso_contenido_f303",
                        ((org.postgresql.util.PSQLException) error).getServerErrorMessage().getConstraint());
            }
        }
        System.out.println("BD0052 F3-03 SQL real V007: Publicado programado, marca persistida y edición rechazada. Sin servicio M3.");
    }

    @Test
    void rollbackRevierteSesionYMarcaAcademicaEnMismaTransaccion() throws SQLException {
        UUID publicado = caso("Publicado");
        try (var connection = conexion()) {
            connection.setAutoCommit(false);
            programar(connection, publicado);
            connection.rollback();
        }
        try (var connection = conexion(); var sql = connection.prepareStatement(
                "SELECT \"fechaBloqueoAcademico\",(SELECT count(*) FROM \"Sesion\") FROM \"Caso\" WHERE \"idCaso\"=?")) {
            sql.setObject(1, publicado);
            try (var resultado = sql.executeQuery()) {
                assertTrue(resultado.next());
                assertNull(resultado.getObject(1));
                assertEquals(0, resultado.getInt(2));
            }
        }
        System.out.println("BD0052 rollback SQL real: sesiones=0 y marca F3-03=NULL tras rollback; conexión independiente.");
    }

    @Test
    void diagnosticoV010CreaDosTablasParalelasYEludeTriggerOficial() throws SQLException {
        try (var connection = conexion(); var sql = connection.createStatement(); var resultado = sql.executeQuery(
                "SELECT count(*) FROM information_schema.tables WHERE table_schema='public' AND table_type='BASE TABLE' AND table_name<>'flyway_schema_history'")) {
            assertTrue(resultado.next());
            assertEquals(21, resultado.getInt(1));
        }
        migraciones("10").migrate();
        UUID borrador = caso("Borrador");
        try (var connection = conexion(); var sql = connection.prepareStatement(
                "INSERT INTO sesiones_academicas (id,caso_id,docente_id,titulo,fecha_inicio,fecha_fin) "
                + "VALUES (?,?,?,'Diagnóstico',now()+interval '1 day',now()+interval '2 days')")) {
            sql.setObject(1, UUID.randomUUID());
            sql.setObject(2, borrador);
            sql.setObject(3, docente);
            assertEquals(1, sql.executeUpdate());
            try (var count = connection.createStatement(); var resultado = count.executeQuery(
                    "SELECT count(*) FROM information_schema.tables WHERE table_schema='public' AND table_type='BASE TABLE' AND table_name<>'flyway_schema_history'")) {
                assertTrue(resultado.next());
                assertEquals(23, resultado.getInt(1));
            }
        }
        System.out.println("BLOQUEO CONFIRMADO: V001-V009=21 tablas; +V010=23; sesiones_academicas permite caso Borrador por SQL y no activa RF09/F3-03 oficiales.");
    }
}
