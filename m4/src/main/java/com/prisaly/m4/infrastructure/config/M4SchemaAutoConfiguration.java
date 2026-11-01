package com.prisaly.m4.infrastructure.config;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.SmartInitializingSingleton;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.jdbc.core.JdbcTemplate;

import javax.sql.DataSource;

/** Verifica después de inicializar Flyway/JPA; nunca corrige o elimina datos. */
@AutoConfiguration
public class M4SchemaAutoConfiguration {
    @Bean
    SmartInitializingSingleton verificarEsquemaM4(ObjectProvider<DataSource> bases) {
        return () -> {
            DataSource base = bases.getIfAvailable();
            if (base == null) return;
            verificar(new JdbcTemplate(base));
        };
    }

    public static void verificar(JdbcTemplate jdbc) {
        Integer legado =
                jdbc.queryForObject(
                        """
SELECT count(*) FROM information_schema.tables
WHERE table_schema='public' AND table_name IN ('sesiones_academicas','convocatorias')
""",
                        Integer.class);
        Integer oficiales =
                jdbc.queryForObject(
                        """
SELECT count(*) FROM information_schema.tables
WHERE table_schema='public' AND table_type='BASE TABLE' AND table_name<>'flyway_schema_history'
""",
                        Integer.class);
        if (legado == null || legado != 0 || oficiales == null || oficiales != 21) {
            throw new IllegalStateException(
                    "M4-ESQUEMA-PENDIENTE: se requieren las 21 tablas oficiales sin tablas"
                        + " paralelas; revisar V010 con Dirección Técnica");
        }
    }
}
