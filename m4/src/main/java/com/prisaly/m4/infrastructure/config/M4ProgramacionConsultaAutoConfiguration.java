package com.prisaly.m4.infrastructure.config;

import com.prisaly.m1.application.port.StudentAccessPort;
import com.prisaly.m1.api.IdentityDomainException;
import com.prisaly.m4.api.ConsultaProgramacionNoDisponibleException;
import com.prisaly.m4.api.ContextoProgramacion;
import com.prisaly.m4.api.ProgramacionAutenticadaPort;
import com.prisaly.m4.api.ProgramacionInicioCoordinadoPort;
import com.prisaly.m4.api.ProgramacionParticipacionPort;
import com.prisaly.m4.infrastructure.adapter.ProgramacionAutenticadaM1Adapter;
import com.prisaly.m4.infrastructure.adapter.ProgramacionInicioCoordinadoJdbcAdapter;
import com.prisaly.m4.infrastructure.adapter.ProgramacionParticipacionJdbcAdapter;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.Optional;
import java.util.UUID;

/**
 * Arma los beans con los que M5 pregunta a M4 si una sesión y una convocatoria existen.
 * Si falta la base, la consulta queda no disponible en lugar de autorizar.
 */
@AutoConfiguration
public class M4ProgramacionConsultaAutoConfiguration {
    @Bean
    @ConditionalOnMissingBean(ProgramacionInicioCoordinadoPort.class)
    ProgramacionInicioCoordinadoPort inicioCoordinado(
            ObjectProvider<JdbcTemplate> bases, ProgramacionParticipacionPort consulta) {
        var jdbc = bases.getIfAvailable();
        if (jdbc == null) {
            return (convocatoria, estudiante) -> {
                throw new ConsultaProgramacionNoDisponibleException();
            };
        }
        return new ProgramacionInicioCoordinadoJdbcAdapter(jdbc, consulta);
    }

    @Bean
    @ConditionalOnMissingBean(ProgramacionAutenticadaPort.class)
    ProgramacionAutenticadaPort consultaAutenticada(
            ProgramacionParticipacionPort programacion,
            ObjectProvider<StudentAccessPort> proveedores) {
        StudentAccessPort identidad =
                proveedores.getIfAvailable(
                        () ->
                                () -> {
                                    throw new IdentityDomainException(
                                            "STUDENT_IDENTITY_UNAVAILABLE",
                                            "No hay proveedor estudiantil");
                                });
        return new ProgramacionAutenticadaM1Adapter(identidad, programacion);
    }

    @Bean
    @ConditionalOnMissingBean(ProgramacionParticipacionPort.class)
    @ConditionalOnProperty(name = "prisaly.m4.consulta-bd0052.enabled", havingValue = "true")
    ProgramacionParticipacionPort consultaBd0052(JdbcTemplate jdbc) {
        return new ProgramacionParticipacionJdbcAdapter(jdbc);
    }

    @Bean
    @ConditionalOnMissingBean(ProgramacionParticipacionPort.class)
    @ConditionalOnProperty(
            name = "prisaly.m4.consulta-bd0052.enabled",
            havingValue = "false",
            matchIfMissing = true)
    ProgramacionParticipacionPort consultaNoDisponible() {
        return new ProgramacionParticipacionPort() {
            @Override
            public Optional<ContextoProgramacion> consultarSesion(UUID sesion, UUID estudiante) {
                throw new ConsultaProgramacionNoDisponibleException();
            }

            @Override
            public Optional<ContextoProgramacion> consultarConvocatoria(
                    UUID convocatoria, UUID estudiante) {
                throw new ConsultaProgramacionNoDisponibleException();
            }
        };
    }
}
