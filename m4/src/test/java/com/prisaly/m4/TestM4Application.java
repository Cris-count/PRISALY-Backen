package com.prisaly.m4;

import com.prisaly.m4.infrastructure.adapter.IdentidadStubAdapter;
import com.prisaly.m4.infrastructure.adapter.NotificacionStubAdapter;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Import;

/**
 * Aplicación de prueba para cargar el contexto de Spring Boot en los tests de integración de M4.
 */
@SpringBootApplication
@Import({IdentidadStubAdapter.class, NotificacionStubAdapter.class})
public class TestM4Application {
    @org.springframework.context.annotation.Bean
    com.prisaly.m4.application.port.ParticipacionesPort historialDePrueba(
            org.springframework.jdbc.core.JdbcTemplate jdbc) {
        return id ->
                Boolean.TRUE.equals(
                        jdbc.queryForObject(
                                "SELECT EXISTS(SELECT 1 FROM public.\"Participacion\" p JOIN"
                                    + " public.\"Convocatoria\" c ON"
                                    + " c.\"idConvocatoria\"=p.\"idConvocatoria\" WHERE"
                                    + " c.\"idSesion\"=?)",
                                Boolean.class,
                                id));
    }

    public static void main(String[] args) {
        SpringApplication.run(TestM4Application.class, args);
    }
}
