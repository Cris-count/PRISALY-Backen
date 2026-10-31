package com.prisaly.m3.infrastructure;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.sql.DriverManager;
import org.junit.jupiter.api.Test;
import org.testcontainers.postgresql.PostgreSQLContainer;

/** Infrastructure check only; does not certify M3 persistence or BD-005.2. */
class PostgreSqlDockerIT {
    @Test
    void connectsToPostgreSql16InDocker() throws Exception {
        try (var postgres = new PostgreSQLContainer("postgres:16.15")) {
            postgres.start();
            try (var connection = DriverManager.getConnection(
                    postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword());
                 var statement = connection.createStatement();
                 var result = statement.executeQuery(
                         "SELECT current_setting('server_version_num')::integer, 1")) {
                assertTrue(result.next());
                assertTrue(result.getInt(1) >= 160000 && result.getInt(1) < 170000);
                assertEquals(1, result.getInt(2));
            }
        }
    }
}