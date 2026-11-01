package com.prisaly.m4.infrastructure;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.testcontainers.postgresql.PostgreSQLContainer;

@DisplayName("Verificación de conexión a PostgreSQL 16 en Docker (Fase 1)")
class PostgreSqlDockerSmokeTest {

    @Test
    @DisplayName("Debe conectarse a un contenedor PostgreSQL 16 en Docker y ejecutar consultas")
    void testPostgreSqlConnection() throws Exception {
        try (var postgres = new PostgreSQLContainer("postgres:16-alpine")) {
            postgres.start();

            assertTrue(postgres.isRunning(), "El contenedor de PostgreSQL debe estar en ejecución");

            try (Connection conn = DriverManager.getConnection(
                    postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword());
                 Statement stmt = conn.createStatement();
                 ResultSet rs = stmt.executeQuery("SELECT current_setting('server_version_num')::integer, 1")) {

                assertTrue(rs.next());
                int versionNum = rs.getInt(1);
                int one = rs.getInt(2);

                assertTrue(versionNum >= 160000 && versionNum < 170000, 
                        "La versión de PostgreSQL debe ser 16.x (actual: " + versionNum + ")");
                assertEquals(1, one);
            }
        }
    }
}
