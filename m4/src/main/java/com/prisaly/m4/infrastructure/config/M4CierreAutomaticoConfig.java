package com.prisaly.m4.infrastructure.config;

import com.prisaly.m4.application.service.SesionAcademicaService;
import java.time.Duration;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;

/**
 * DD-01 (DC-90/DC-92, ratificada) — cierre automático de sesiones. Solo existe con
 * {@code prisaly.m4.cierre-automatico.enabled=true} (valor entregado: {@code false}).
 *
 * <p>Cada ciclo busca las sesiones {@code EnCurso} cuyo {@code finPrevisto + margen} ya pasó según PostgreSQL y las
 * cierra una a una con {@link SesionAcademicaService#cerrarAutomaticamente}, en su propia transacción. El margen por
 * defecto es {@code P1D}, el texto de RF-19 V27 («un día después del fin previsto»). Los nuevos inicios ya están
 * impedidos desde el fin previsto (RF-22 y L1), así que el margen no amplía ningún acceso.
 */
@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(name = M4CierreAutomaticoConfig.PROPIEDAD, havingValue = "true")
@EnableScheduling
public class M4CierreAutomaticoConfig {
    public static final String PROPIEDAD = "prisaly.m4.cierre-automatico.enabled";

    @Bean
    CierreAutomaticoSesiones cierreAutomaticoSesiones(SesionAcademicaService sesiones, JdbcTemplate jdbc,
            @Value("${prisaly.m4.cierre-automatico.margen:P1D}") Duration margen) {
        if (margen.isNegative()) {
            throw new IllegalStateException("M4-CIERRE-AUTOMATICO-MARGEN-INVALIDO: el margen no puede ser negativo");
        }
        return new CierreAutomaticoSesiones(sesiones, jdbc, margen);
    }

    /** The job; {@link #ejecutarAhora()} is also the entry point used by the tests. */
    public static final class CierreAutomaticoSesiones {
        private static final Logger log = LoggerFactory.getLogger(CierreAutomaticoSesiones.class);
        private final SesionAcademicaService sesiones;
        private final JdbcTemplate jdbc;
        private final Duration margen;

        CierreAutomaticoSesiones(SesionAcademicaService sesiones, JdbcTemplate jdbc, Duration margen) {
            this.sesiones = Objects.requireNonNull(sesiones, "sesiones");
            this.jdbc = Objects.requireNonNull(jdbc, "jdbc");
            this.margen = Objects.requireNonNull(margen, "margen");
        }

        @Scheduled(fixedDelayString = "${prisaly.m4.cierre-automatico.intervalo-ms:60000}")
        void programado() {
            ejecutarAhora();
        }

        /** @return sessions closed by this cycle */
        public int ejecutarAhora() {
            List<UUID> candidatas = jdbc.queryForList("SELECT \"idSesion\" FROM \"Sesion\" WHERE \"estado\"='EnCurso'"
                    + " AND \"finPrevisto\" + (? * interval '1 second') <= now() ORDER BY \"finPrevisto\"",
                    UUID.class, margen.toSeconds());
            int cerradas = 0;
            for (UUID id : candidatas) {
                if (sesiones.cerrarAutomaticamente(id, margen)) {
                    cerradas++;
                    log.info("DD-01 cierre automatico de sesion {}", id);
                }
            }
            return cerradas;
        }
    }
}
