package com.prisaly.m4.infrastructure.config;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.SmartInitializingSingleton;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.jdbc.core.JdbcTemplate;

import javax.sql.DataSource;

/** Verifica después de inicializar Flyway/JPA; nunca corrige o elimina datos. */
@AutoConfiguration
public class M4SchemaAutoConfiguration {
    @Bean
    SmartInitializingSingleton verificarEsquemaM4(ObjectProvider<DataSource> bases,
            @Value("${prisaly.m4.schema-contract:" + M4SchemaContract.VIGENTE + "}") String contrato) {
        return () -> {
            DataSource base = bases.getIfAvailable();
            if (base == null) return;
            verificar(new JdbcTemplate(base), contrato);
        };
    }

    public static void verificar(JdbcTemplate jdbc) {
        verificar(jdbc, M4SchemaContract.VIGENTE);
    }

    public static void verificar(JdbcTemplate jdbc, String contrato) {
        M4SchemaContract.verificar(jdbc, contrato);
    }
}
