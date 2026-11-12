package com.prisaly.m1.infrastructure.security;

import com.prisaly.m1.application.service.PurgaAccesoEstudianteService;
import com.prisaly.m1.domain.student.ClavesAccesoEstudiante;
import com.prisaly.m1.domain.student.ConjuntoClavesHmac;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;

/**
 * D-B5 (DC-DD02-B4-B5-01). With the student-access flag ON the application does NOT start when K-COD or K-SES is
 * missing, malformed, shorter than 32 bytes, or shared with the other key (fail closed). Keys come only from the
 * operator's environment or secret files ({@code PRISALY_ESTUDIANTE_CLAVE_CODIGO} / {@code _SESION}); there is no
 * default value. Format: {@code kid:base64[,kid:base64...]}. Only key ids are ever logged.
 * Replaces the former single 16-byte {@code hmac-secret}.
 */
@Configuration
@ConditionalOnProperty(name = "prisaly.m1.student-access.enabled", havingValue = "true")
@EnableScheduling
public class StudentAccessKeyConfig {

    private static final Logger log = LoggerFactory.getLogger("prisaly.auditoria.seguridad");

    @Bean
    ClavesAccesoEstudiante clavesAccesoEstudiante(
            @Value("${prisaly.m1.student-access.claves.codigo:}") String codigo,
            @Value("${prisaly.m1.student-access.claves.sesion:}") String sesion) {
        ClavesAccesoEstudiante claves = new ClavesAccesoEstudiante(
                ConjuntoClavesHmac.leer("K-COD", codigo), ConjuntoClavesHmac.leer("K-SES", sesion));
        log.info("SEGURIDAD_ESTUDIANTE evento=CLAVES_CARGADAS kCod={} kSes={}",
                claves.codigo().kidActual(), claves.sesion().kidActual());
        return claves;
    }

    /** D-B2.4: the purge runs whenever the access is enabled; it cannot be switched off independently. */
    @Bean
    PurgaProgramada purgaAccesoEstudianteProgramada(PurgaAccesoEstudianteService purga) {
        return new PurgaProgramada(purga);
    }

    public static final class PurgaProgramada {
        private final PurgaAccesoEstudianteService purga;

        PurgaProgramada(PurgaAccesoEstudianteService purga) {
            this.purga = purga;
        }

        @Scheduled(fixedDelayString = "${prisaly.m1.student-access.retencion.intervalo-ms:900000}",
                initialDelayString = "${prisaly.m1.student-access.retencion.intervalo-ms:900000}")
        public void ejecutar() {
            var r = purga.purgar();
            if (r.credenciales() + r.sesiones() + r.auditoria() > 0) {
                log.info("SEGURIDAD_ESTUDIANTE evento=PURGA credenciales={} sesiones={} auditoria={}",
                        r.credenciales(), r.sesiones(), r.auditoria());
            }
        }
    }
}
