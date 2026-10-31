package com.prisaly.m4.infrastructure.config;

import java.util.Optional;
import java.util.UUID;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.jdbc.core.JdbcTemplate;

import com.prisaly.m4.api.ConsultaProgramacionNoDisponibleException;
import com.prisaly.m4.api.ContextoProgramacion;
import com.prisaly.m4.api.ProgramacionParticipacionPort;
import com.prisaly.m4.infrastructure.adapter.ProgramacionParticipacionJdbcAdapter;

@AutoConfiguration
public class M4ProgramacionConsultaAutoConfiguration {
    @Bean
    @ConditionalOnMissingBean(ProgramacionParticipacionPort.class)
    @ConditionalOnProperty(name = "prisaly.m4.consulta-bd0052.enabled", havingValue = "true")
    ProgramacionParticipacionPort consultaBd0052(JdbcTemplate jdbc) {
        return new ProgramacionParticipacionJdbcAdapter(jdbc);
    }

    @Bean
    @ConditionalOnMissingBean(ProgramacionParticipacionPort.class)
    @ConditionalOnProperty(name = "prisaly.m4.consulta-bd0052.enabled", havingValue = "false", matchIfMissing = true)
    ProgramacionParticipacionPort consultaNoDisponible() {
        return new ProgramacionParticipacionPort() {
            @Override
            public Optional<ContextoProgramacion> consultarSesion(UUID sesion, UUID estudiante) {
                throw new ConsultaProgramacionNoDisponibleException();
            }

            @Override
            public Optional<ContextoProgramacion> consultarConvocatoria(UUID convocatoria, UUID estudiante) {
                throw new ConsultaProgramacionNoDisponibleException();
            }
        };
    }
}
