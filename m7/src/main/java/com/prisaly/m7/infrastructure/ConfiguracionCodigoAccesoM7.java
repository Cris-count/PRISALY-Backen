package com.prisaly.m7.infrastructure;

import com.prisaly.m1.application.port.RevocacionCodigoAccesoPort;
import com.prisaly.m1.application.port.StudentAccessDeliveryPort;
import com.prisaly.m7.application.EntregaCodigoAccesoRepositorio;
import com.prisaly.m7.application.ProcesarEntregasCodigoAccesoService;
import java.time.Clock;
import java.time.Duration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;

/**
 * DD-02 alternative B in M7 (DC-DD02-B4-B5-01), registered only with {@code prisaly.m1.student-access.enabled=true}:
 * <ul>
 *   <li>K-OUT is mandatory: the application does not start when it is missing or invalid (D-B5);</li>
 *   <li>the encrypted Outbox replaces the in-memory delivery adapter;</li>
 *   <li>the retention purge (payload erased at expiry, metadata 30 days, D-B2.4) always runs with the access;</li>
 *   <li>the code worker is independent from the convocation worker and DISABLED by default
 *       ({@code prisaly.m7.codigo-acceso.worker.enabled}); real institutional SMTP is D-B3.</li>
 * </ul>
 */
@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(name = "prisaly.m1.student-access.enabled", havingValue = "true")
@EnableScheduling
public class ConfiguracionCodigoAccesoM7 {

    private static final Logger log = LoggerFactory.getLogger("prisaly.auditoria.seguridad");

    @Bean
    CifradorCodigoAccesoAesGcm cifradorCodigoAccesoM7(@Value("${prisaly.m7.codigo-acceso.clave-cifrado:}") String clave) {
        CifradorCodigoAccesoAesGcm cifrador = CifradorCodigoAccesoAesGcm.leer(clave);
        log.info("SEGURIDAD_ESTUDIANTE evento=CLAVE_CARGADA kOut={}", cifrador.kidActual());
        return cifrador;
    }

    @Bean
    StudentAccessDeliveryPort studentAccessDeliveryM7(JdbcTemplate jdbc, CifradorCodigoAccesoAesGcm cifrador) {
        return new OutboxCodigoAccesoAdapter(jdbc, cifrador);
    }

    @Bean
    EntregaCodigoAccesoRepositorio entregaCodigoAccesoRepositorioM7(JdbcTemplate jdbc) {
        return new EntregaCodigoAccesoJdbc(jdbc);
    }

    @Bean
    PurgaEntregas purgaEntregasCodigoAccesoM7(EntregaCodigoAccesoRepositorio repositorio,
            @Value("${prisaly.m7.codigo-acceso.retencion.metadatos-dias:30}") int dias) {
        return new PurgaEntregas(repositorio, dias, Clock.systemUTC());
    }

    @Bean
    @ConditionalOnProperty(prefix = "prisaly.m7.codigo-acceso.worker", name = "enabled", havingValue = "true")
    TrabajoEntregas trabajoEntregasCodigoAccesoM7(EntregaCodigoAccesoRepositorio repositorio,
            CifradorCodigoAccesoAesGcm cifrador, JavaMailSender mailSender, RevocacionCodigoAccesoPort revocacion,
            Environment environment) {
        String remitente = M7WorkerConfiguration.requerido(environment, "prisaly.m7.correo.remitente");
        int lote = M7WorkerConfiguration.enteroRequerido(environment, "prisaly.m7.codigo-acceso.worker.lote");
        // Not a ProveedorCorreoPort bean: it must not collide with the convocation worker's provider.
        return new TrabajoEntregas(new ProcesarEntregasCodigoAccesoService(repositorio, cifrador,
                new JavaMailProveedorCorreoAdapter(mailSender), revocacion, remitente, lote, Clock.systemUTC()));
    }

    /** D-B2.4: payload erased at expiry even without the worker; final metadata deleted after N days. */
    public static final class PurgaEntregas {
        private final EntregaCodigoAccesoRepositorio repositorio;
        private final int dias;
        private final Clock reloj;

        public PurgaEntregas(EntregaCodigoAccesoRepositorio repositorio, int dias, Clock reloj) {
            if (dias < 1 || dias > 30) {
                throw new IllegalArgumentException("Los metadatos de la cola se conservan entre 1 y 30 días (D-B2.4)");
            }
            this.repositorio = repositorio;
            this.dias = dias;
            this.reloj = reloj;
        }

        @Scheduled(fixedDelayString = "${prisaly.m7.codigo-acceso.retencion.intervalo-ms:60000}",
                initialDelayString = "${prisaly.m7.codigo-acceso.retencion.intervalo-ms:60000}")
        public void ejecutar() {
            var ahora = reloj.instant();
            int vencidas = repositorio.cancelarSinTiempo(ahora, ahora);
            int borradas = repositorio.purgarMetadatos(ahora.minus(Duration.ofDays(dias)));
            if (vencidas + borradas > 0) {
                log.info("SEGURIDAD_ESTUDIANTE evento=PURGA_COLA vencidas={} metadatosBorrados={}", vencidas, borradas);
            }
        }
    }

    public static final class TrabajoEntregas {
        private final ProcesarEntregasCodigoAccesoService servicio;

        TrabajoEntregas(ProcesarEntregasCodigoAccesoService servicio) {
            this.servicio = servicio;
        }

        @Scheduled(fixedDelayString = "${prisaly.m7.codigo-acceso.worker.intervalo-ms:5000}")
        public void ejecutar() {
            servicio.procesar();
        }

        public int ejecutarAhora() {
            return servicio.procesar();
        }
    }
}
