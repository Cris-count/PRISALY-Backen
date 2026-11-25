package com.prisaly.m1.infrastructure.estudiantes;

import com.prisaly.m1.application.port.EstudianteRegistroPort;
import com.prisaly.m1.application.port.RolRepositorioPort;
import com.prisaly.m1.application.service.AdministracionIdentidadService;
import com.prisaly.m1.application.service.AltaEstudianteService;
import com.prisaly.m1.application.service.IdentityCatalogService;
import com.prisaly.m1.infrastructure.persistence.EstudianteRegistroJdbcAdapter;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * P-78 (DC-BE-D78-1-AUTORIZACION-CONTROLADA-79): assembly of the administrative student registration, QA only.
 * OFF by default ({@code prisaly.m1.alta-estudiantes.enabled}). Uses the official schema only. The student role
 * ({@code prisaly.m1.alta-estudiantes.rol}, default {@code ESTUDIANTE}) is catalog data (PD-01 extension
 * {@code docs/provision/pd01-p78-rol-estudiante.sql}); without it the operation answers 503.
 */
@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(name = "prisaly.m1.alta-estudiantes.enabled", havingValue = "true")
public class AltaEstudiantesConfig {

    @Bean
    EstudianteRegistroPort estudianteRegistro(JdbcTemplate jdbc) {
        return new EstudianteRegistroJdbcAdapter(jdbc);
    }

    @Bean
    AltaEstudianteService altaEstudianteService(AdministracionIdentidadService administracion,
            IdentityCatalogService catalogo, RolRepositorioPort roles, EstudianteRegistroPort estudiantes,
            PlatformTransactionManager transacciones, Environment env) {
        return new AltaEstudianteService(administracion, catalogo, roles, estudiantes,
                new TransactionTemplate(transacciones), env.getProperty("prisaly.m1.alta-estudiantes.rol", "ESTUDIANTE"));
    }
}
