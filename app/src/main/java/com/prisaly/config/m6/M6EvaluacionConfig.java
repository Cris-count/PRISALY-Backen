package com.prisaly.config.m6;

import com.prisaly.m1.application.port.IdentityAccessPort;
import com.prisaly.m3.infrastructure.persistence.jdbc.AutoriaCasoJdbcAdapter;
import com.prisaly.m3.infrastructure.persistence.jpa.CasoJpaAggregateReader;
import com.prisaly.m3.infrastructure.persistence.jpa.ContextoAcademicoProtectedReader;
import com.prisaly.m4.infrastructure.adapter.ResponsableConvocatoriaJdbcAdapter;
import com.prisaly.m5.application.port.RepositorioIntentos;
import com.prisaly.m5.infrastructure.evaluacion.LecturaIntentoDocenteDesdeM5;
import com.prisaly.m6.application.port.ConsultaCalificacionPort;
import com.prisaly.m6.application.proxy.EvaluacionProtectionProxy;
import com.prisaly.m6.application.service.EvaluarIntentoService;
import com.prisaly.m6.application.service.PrepararContextoEvaluacionService;
import com.prisaly.m6.application.service.PrepararEvaluacionProtegida;
import com.prisaly.m6.domain.strategy.EstrategiaPonderadaS1;
import com.prisaly.m6.infrastructure.persistence.jdbc.CalificacionJdbcWriter;
import java.time.Clock;
import java.util.Map;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;

/**
 * DC-BE-M6-DESBLOQUEO-IMPLEMENTACION-55: switch-guarded assembly of the teacher evaluation. With
 * {@code prisaly.m6.evaluacion.enabled} absent or {@code false} (the shipped value) nothing here exists and the
 * routes answer 404. Only one bean is produced, {@link EvaluacionDocenteM6}; the authorization, the capability
 * issuer/verifier, the M3 protected reader, the B1 reader and the writer are private to it.
 */
@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(prefix = "prisaly.m6.evaluacion", name = "enabled", havingValue = "true")
public class M6EvaluacionConfig {

    @Bean
    EvaluacionDocenteM6 evaluacionDocenteM6(IdentityAccessPort identidad, RepositorioIntentos intentos,
            CasoJpaAggregateReader casos, ConsultaCalificacionPort calificaciones, JdbcTemplate jdbc,
            PlatformTransactionManager transacciones) {
        var propietario = new PropietarioActivoCasoM6(identidad, new AutoriaCasoJdbcAdapter(jdbc));
        var lector = new LecturaIntentoDocenteDesdeM5(intentos, new ResponsableConvocatoriaJdbcAdapter(jdbc),
                propietario, transacciones);
        var autorizacion = new AutorizacionDocentePropietarioM6(lector);
        var capacidades = new CapacidadesContextoEvaluacionM6(propietario, Clock.systemUTC());
        var contexto = new ContextoAcademicoM3ParaM6(new ContextoAcademicoProtectedReader(capacidades, casos));
        var preparacion = new PrepararEvaluacionProtegida(autorizacion, calificaciones,
                new PrepararContextoEvaluacionService(lector, capacidades, contexto));
        var s1 = new EstrategiaPonderadaS1();
        var evaluar = new EvaluarIntentoService(preparacion, Map.of(EstrategiaPonderadaS1.IDENTIFICADOR, s1),
                EstrategiaPonderadaS1.IDENTIFICADOR, new CalificacionJdbcWriter(jdbc));
        return new EvaluacionDocenteM6(new EvaluacionProtectionProxy(autorizacion, evaluar), lector, calificaciones);
    }
}
