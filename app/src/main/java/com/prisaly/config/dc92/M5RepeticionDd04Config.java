package com.prisaly.config.dc92;

import com.prisaly.m1.application.port.IdentityAccessPort;
import com.prisaly.m3.infrastructure.persistence.jdbc.AutoriaCasoJdbcAdapter;
import com.prisaly.m4.infrastructure.adapter.ResponsableConvocatoriaJdbcAdapter;
import com.prisaly.m5.application.repeticion.AutorizarRepeticionServicio;
import com.prisaly.m5.infrastructure.persistencia.AutorizacionRepeticionJdbc;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * DC-92 DD-04 (ratificada). With {@code prisaly.m5.repeticion.enabled} absent or {@code false} (shipped) nothing exists:
 * no teacher route (404) and M5 keeps rejecting any repetition exactly as before. Turning it on requires the DC-92
 * schema (V021, {@code SeleccionIntento} + one-repetition CHECK); the M4 contract guard rejects a base without it.
 */
@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(name = M5RepeticionDd04Config.PROPIEDAD, havingValue = "true")
public class M5RepeticionDd04Config {
    public static final String PROPIEDAD = "prisaly.m5.repeticion.enabled";

    @Bean
    RepeticionDocenteDd04 repeticionDocenteDd04(IdentityAccessPort identidad, JdbcTemplate jdbc,
            PlatformTransactionManager transacciones) {
        var responsables = new ResponsableConvocatoriaJdbcAdapter(jdbc);
        var autoria = new AutoriaCasoJdbcAdapter(jdbc);
        // DD-04 ratified by DC-92: the policy that was RepeticionNoRatificada is now enabled.
        var servicio = new AutorizarRepeticionServicio(() -> true, new AutorizacionRepeticionJdbc(jdbc), responsables, autoria);
        return new RepeticionDocenteDd04(servicio, responsables, autoria, identidad, jdbc,
                new TransactionTemplate(transacciones));
    }
}
