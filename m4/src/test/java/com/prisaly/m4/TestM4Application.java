package com.prisaly.m4;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Import;
import com.prisaly.m4.infrastructure.adapter.IdentidadStubAdapter;
import com.prisaly.m4.infrastructure.adapter.CasoAcademicoStubAdapter;
import com.prisaly.m4.infrastructure.adapter.NotificacionStubAdapter;
import com.prisaly.m4.infrastructure.adapter.ParticipacionesStubAdapter;

/**
 * Aplicación de prueba para cargar el contexto de Spring Boot en los tests de integración de M4.
 */
@SpringBootApplication
@Import({IdentidadStubAdapter.class, CasoAcademicoStubAdapter.class,
        NotificacionStubAdapter.class, ParticipacionesStubAdapter.class})
public class TestM4Application {
    public static void main(String[] args) {
        SpringApplication.run(TestM4Application.class, args);
    }
}
