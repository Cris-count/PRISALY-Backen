package com.prisaly.m3.infrastructure.config;

import com.prisaly.m3.application.port.CaseAuthorizationPort;
import com.prisaly.m3.application.port.CasoCurrentUserPort;
import com.prisaly.m3.application.port.UnidadTransaccionalNarrativa;
import com.prisaly.m3.application.service.GuionNarrativoService;
import com.prisaly.m3.domain.narrativa.CatalogoVisual;
import com.prisaly.m3.domain.narrativa.ValidadorGuionNarrativo;
import com.prisaly.m3.infrastructure.persistence.jdbc.GuionNarrativoJdbcAdapter;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Ensamblaje candidato del guion narrativo (DC-BE-SIM-INCREMENTO-A-15). <b>Desactivado por defecto</b>: solo existe
 * con {@value #PROPIEDAD}{@code =true}, que además exige el esquema candidato V017 (contratos QA
 * {@code bd0052-v013-sim-a} / {@code bd0052-v013-dd02b-sim-a}). Con el valor productivo ({@code false}) no se registra
 * ningún bean, ni el puerto hacia M5, ni el controlador docente.
 *
 * <p>Catálogo visual: el recurso canónico {@link CatalogoVisualRecurso#VIGENTE} (D-SIM-7 aprobada en DC-CB-03). Sustituye
 * a la lista plana {@code prisaly.m3.narrativa.recursos-visuales}, que no figuraba en {@code application.yml}.
 */
@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(name = M3NarrativaConfig.PROPIEDAD, havingValue = "true")
public class M3NarrativaConfig {
    public static final String PROPIEDAD = "prisaly.m3.narrativa.publicacion.enabled";

    @Bean
    GuionNarrativoJdbcAdapter guionNarrativoJdbcAdapter(JdbcTemplate jdbc) {
        return new GuionNarrativoJdbcAdapter(jdbc);
    }

    @Bean
    CatalogoVisual catalogoVisual() {
        return CatalogoVisualRecurso.cargarVigente();
    }

    @Bean
    ValidadorGuionNarrativo validadorGuionNarrativo(CatalogoVisual catalogo) {
        return new ValidadorGuionNarrativo(catalogo);
    }

    @Bean
    GuionNarrativoService guionNarrativoService(ObjectProvider<CasoCurrentUserPort> identidad,
            ObjectProvider<CaseAuthorizationPort> autorizacion, GuionNarrativoJdbcAdapter guiones,
            PlatformTransactionManager transacciones, ValidadorGuionNarrativo validador) {
        TransactionTemplate plantilla = new TransactionTemplate(transacciones);
        UnidadTransaccionalNarrativa unidad = new UnidadTransaccionalNarrativa() {
            @Override
            public <T> T ejecutar(java.util.function.Supplier<T> casoDeUso) {
                return plantilla.execute(estado -> casoDeUso.get());
            }
        };
        return new GuionNarrativoService(identidad.getIfAvailable(), autorizacion.getIfAvailable(), guiones, unidad, validador);
    }
}
