package com.prisaly.config;

import static org.junit.jupiter.api.Assertions.*;

import com.prisaly.PrisalyApplication;

import java.security.SecureRandom;
import java.util.Base64;

import org.junit.jupiter.api.Test;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

/**
 * DD-02 B (CIERRE-07, P2) on the real application and PostgreSQL 16.15: with the student-access flag on, the shipped
 * Flyway location (V001-V009 + V013, without V014-V016) and the default schema contract, startup fails closed with
 * {@code DD02-ESQUEMA-REQUERIDO} instead of serving student endpoints over missing tables. Disposable random keys only.
 */
@Testcontainers
class EsquemaAccesoEstudiantePostgreSqlIT {
    @Container static PostgreSQLContainer db = new PostgreSQLContainer("postgres:16.15");

    private static String clave(String kid) {
        byte[] v = new byte[32];
        new SecureRandom().nextBytes(v);
        return kid + ":" + Base64.getEncoder().encodeToString(v);
    }

    @Test
    void conElFlagSobreElEsquemaProductivoLaAplicacionNoArranca() {
        var error = assertThrows(Exception.class, () -> new SpringApplicationBuilder(PrisalyApplication.class).run(
                "--server.port=0", "--server.address=127.0.0.1",
                "--spring.datasource.url=" + db.getJdbcUrl(), "--spring.datasource.username=" + db.getUsername(),
                "--spring.datasource.password=" + db.getPassword(),
                "--prisaly.m1.student-access.enabled=true",
                "--prisaly.m1.student-access.claves.codigo=" + clave("cod-it"),
                "--prisaly.m1.student-access.claves.sesion=" + clave("ses-it"),
                "--prisaly.m7.codigo-acceso.clave-cifrado=" + clave("out-it"),
                "--prisaly.m7.worker.enabled=false").close());
        Throwable causa = error;
        boolean encontrado = false;
        while (causa != null) {
            if (String.valueOf(causa.getMessage()).contains(EsquemaAccesoEstudianteConfig.CODIGO)) { encontrado = true; break; }
            causa = causa.getCause();
        }
        assertTrue(encontrado, "expected " + EsquemaAccesoEstudianteConfig.CODIGO + ", got: " + error);
    }
}
