package com.prisaly.m7.infrastructure;

import com.prisaly.m7.application.PoliticaReintentos;
import com.prisaly.m7.application.ProcesarEnviosPendientesService;
import com.prisaly.m7.application.ProveedorCorreoPort;
import com.prisaly.m7.application.ReclamoEnvioPort;
import jakarta.persistence.EntityManager;
import java.time.Duration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;

/**
 * Email delivery worker (Fase 3). Registered only when {@code prisaly.m7.worker.enabled=true}.
 * Requires explicit worker and mail properties; no silent defaults for operational knobs.
 */
@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(prefix = "prisaly.m7.worker", name = "enabled", havingValue = "true")
@EnableScheduling
public class M7WorkerConfiguration {

    @Bean
    ReclamoEnvioPort reclamoEnvioM7(EntityManager em) {
        return new ReclamoEnvioJpaAdapter(em);
    }

    @Bean
    ProveedorCorreoPort proveedorCorreoM7(JavaMailSender mailSender) {
        return new JavaMailProveedorCorreoAdapter(mailSender);
    }

    @Bean
    PoliticaReintentos politicaReintentosM7() {
        return PoliticaReintentos.ratificada();
    }

    @Bean
    ProcesarEnviosPendientesService procesarEnviosPendientesM7(
            ReclamoEnvioPort reclamo,
            ProveedorCorreoPort proveedor,
            PoliticaReintentos politica,
            Environment environment) {
        String remitente = requerido(environment, "prisaly.m7.correo.remitente");
        int lote = enteroRequerido(environment, "prisaly.m7.worker.lote");
        long leaseSegundos = largoRequerido(environment, "prisaly.m7.worker.lease-segundos");
        return new ProcesarEnviosPendientesService(
                reclamo, proveedor, politica, remitente, lote, Duration.ofSeconds(leaseSegundos));
    }

    @Bean
    EnviosPendientesScheduler enviosPendientesSchedulerM7(ProcesarEnviosPendientesService servicio) {
        return new EnviosPendientesScheduler(servicio);
    }

    static String requerido(Environment environment, String clave) {
        String valor = environment.getProperty(clave);
        if (valor == null || valor.isBlank()) {
            throw new IllegalStateException("Missing required property: " + clave);
        }
        return valor;
    }

    static int enteroRequerido(Environment environment, String clave) {
        String valor = requerido(environment, clave);
        try {
            int parseado = Integer.parseInt(valor);
            if (parseado < 1) {
                throw new IllegalStateException(clave + " must be >= 1");
            }
            return parseado;
        } catch (NumberFormatException error) {
            throw new IllegalStateException(clave + " must be an integer", error);
        }
    }

    static long largoRequerido(Environment environment, String clave) {
        String valor = requerido(environment, clave);
        try {
            long parseado = Long.parseLong(valor);
            if (parseado < 1) {
                throw new IllegalStateException(clave + " must be >= 1");
            }
            return parseado;
        } catch (NumberFormatException error) {
            throw new IllegalStateException(clave + " must be a long", error);
        }
    }

    public static final class EnviosPendientesScheduler {
        private final ProcesarEnviosPendientesService servicio;

        EnviosPendientesScheduler(ProcesarEnviosPendientesService servicio) {
            this.servicio = servicio;
        }

        @Scheduled(fixedDelayString = "${prisaly.m7.worker.intervalo-ms}")
        public void tick() {
            servicio.procesarLote();
        }
    }
}
