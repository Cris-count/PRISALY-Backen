package com.prisaly.contracttests.m4;

import static org.mockito.Mockito.mock;

import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.persistence.autoconfigure.EntityScan;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

import com.prisaly.m1.application.service.IdentityAccessService;
import com.prisaly.m1.infrastructure.persistence.UsuarioEntity;
import com.prisaly.m1.infrastructure.persistence.UsuarioRepository;
import com.prisaly.m4.application.port.*;
import com.prisaly.m4.application.service.SesionAcademicaService;

/** Ensamblaje de pruebas fuera del component-scan de TestM4Application. */
@Configuration(proxyBeanMethods = false)
@EnableAutoConfiguration
@EntityScan(basePackageClasses = UsuarioEntity.class)
@EnableJpaRepositories(basePackageClasses = UsuarioRepository.class)
@Import(IdentityAccessService.class)
public class M1ContractsTestConfiguration {
    @Bean SesionRepository sesiones() { return mock(SesionRepository.class); }
    @Bean ConvocatoriaRepository convocatorias() { return mock(ConvocatoriaRepository.class); }
    @Bean CasoAcademicoPort casos() { return mock(CasoAcademicoPort.class); }
    @Bean NotificacionPort notificaciones() { return mock(NotificacionPort.class); }
    @Bean SesionAcademicaService servicio(SesionRepository sesiones, ConvocatoriaRepository convocatorias,
            IdentidadPort identidad, CasoAcademicoPort casos, NotificacionPort notificaciones) {
        return new SesionAcademicaService(sesiones, convocatorias, identidad, casos, notificaciones);
    }
}
