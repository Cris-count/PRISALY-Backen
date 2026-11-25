package com.prisaly.m1.infrastructure.activacion;

import com.prisaly.m1.application.port.ActivacionDocenteRepositorioPort;
import com.prisaly.m1.application.port.AuditoriaActivacionDocentePort;
import com.prisaly.m1.application.port.ContrasenasBloqueadasPort;
import com.prisaly.m1.application.port.EntregaActivacionDocentePort;
import com.prisaly.m1.application.port.UsuarioRepositorioPort;
import com.prisaly.m1.application.service.ActivacionDocenteService;
import com.prisaly.m1.application.service.AdministracionIdentidadService;
import com.prisaly.m1.application.service.GestionActivacionDocenteService;
import com.prisaly.m1.infrastructure.persistence.ActivacionDocenteJdbcAdapter;
import com.prisaly.m1.infrastructure.security.AuditoriaActivacionDocenteLog;
import com.prisaly.m1.infrastructure.security.RegistroIntentosEnMemoria;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.format.DateTimeParseException;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * D-05 (DC-BE-D05-AUTORIZACION-QA-77): assembly of teacher activation, QA only. OFF by default
 * ({@code prisaly.m1.activacion-docente.enabled}). When ON it requires:
 * <ul>
 *   <li>{@code prisaly.m1.activacion-docente.vigencia}: ISO-8601 duration, mandatory, no default, between
 *       {@link #VIGENCIA_MINIMA} and {@link #VIGENCIA_MAXIMA}; startup fails otherwise
 *       ({@code M1-ACTIVACION-VIGENCIA});</li>
 *   <li>the candidate table {@code "ActivacionDocente"} ({@code db/d05-propuesta}): Hibernate does not validate it,
 *       so a missing table surfaces on first use;</li>
 *   <li>an {@link EntregaActivacionDocentePort} (M7 mail adapter, same switch).</li>
 * </ul>
 * The activation attempt counter is a private in-memory instance (not a bean, so the login limiter is untouched).
 */
@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(name = "prisaly.m1.activacion-docente.enabled", havingValue = "true")
public class ActivacionDocenteConfig {
    public static final Duration VIGENCIA_MINIMA = Duration.ofMinutes(15);
    public static final Duration VIGENCIA_MAXIMA = Duration.ofDays(7);

    static Duration vigencia(Environment env) {
        String valor = env.getProperty("prisaly.m1.activacion-docente.vigencia", "").trim();
        if (valor.isEmpty()) {
            throw new IllegalStateException("M1-ACTIVACION-VIGENCIA: prisaly.m1.activacion-docente.vigencia is mandatory");
        }
        Duration d;
        try {
            d = Duration.parse(valor);
        } catch (DateTimeParseException ex) {
            throw new IllegalStateException("M1-ACTIVACION-VIGENCIA: not an ISO-8601 duration", ex);
        }
        if (d.compareTo(VIGENCIA_MINIMA) < 0 || d.compareTo(VIGENCIA_MAXIMA) > 0) {
            throw new IllegalStateException("M1-ACTIVACION-VIGENCIA: must be between PT15M and P7D");
        }
        return d;
    }

    @Bean
    ActivacionDocenteRepositorioPort activacionDocenteRepositorio(JdbcTemplate jdbc) {
        return new ActivacionDocenteJdbcAdapter(jdbc);
    }

    @Bean
    AuditoriaActivacionDocentePort auditoriaActivacionDocente() {
        return new AuditoriaActivacionDocenteLog();
    }

    @Bean
    ActivacionDocenteService activacionDocenteService(UsuarioRepositorioPort usuarios,
            ActivacionDocenteRepositorioPort activaciones, EntregaActivacionDocentePort entrega,
            AuditoriaActivacionDocentePort auditoria, ContrasenasBloqueadasPort bloqueadas, PasswordEncoder codificador,
            Environment env) {
        return new ActivacionDocenteService(usuarios, activaciones, entrega, auditoria, bloqueadas, codificador,
                Clock.systemUTC(), vigencia(env), new SecureRandom());
    }

    @Bean
    GestionActivacionDocenteService gestionActivacionDocenteService(ActivacionDocenteService nucleo,
            AdministracionIdentidadService administracion, PlatformTransactionManager transacciones, Environment env) {
        int porToken = env.getProperty("prisaly.m1.activacion-docente.intentos.max-por-token", Integer.class, 5);
        int porOrigen = env.getProperty("prisaly.m1.activacion-docente.intentos.max-por-origen", Integer.class, 10);
        Duration ventana = env.getProperty("prisaly.m1.activacion-docente.intentos.ventana", Duration.class,
                Duration.ofMinutes(15));
        return new GestionActivacionDocenteService(nucleo, administracion,
                new RegistroIntentosEnMemoria(Clock.systemUTC(), porToken, porOrigen, ventana),
                new TransactionTemplate(transacciones));
    }
}
