package com.prisaly.config;

import com.prisaly.m4.infrastructure.config.M4SchemaContract;

import java.util.Set;

import org.springframework.beans.factory.SmartInitializingSingleton;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * DC-BE-SIM-B1-B2A-24 (Incremento B, candidato). Las fases académicas del intento persisten en la tabla candidata
 * {@code "FaseIntento"} (V018, {@code db/sim-propuesta-b}), que no forma parte del Flyway productivo. Con el interruptor
 * activo y un contrato de esquema sin V018, el arranque falla cerrado en lugar de fallar después, en tiempo de
 * ejecución, por una tabla ausente. Los contratos aceptados obligan a M4 a comprobar la presencia exacta de
 * {@code "GuionNarrativoCaso"} y {@code "FaseIntento"}. Sin el interruptor (valor por defecto) no hace nada.
 */
@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(name = "prisaly.m5.exploracion.fases.enabled", havingValue = "true")
public class EsquemaFasesIntentoConfig {

    static final String CODIGO = "SIM-FASES-ESQUEMA-REQUERIDO";
    static final Set<String> PERMITIDOS = Set.of(M4SchemaContract.V013_SIM_AB, M4SchemaContract.V013_DD02B_SIM_AB);

    @Bean
    SmartInitializingSingleton verificarEsquemaFasesIntento(
            @Value("${prisaly.m4.schema-contract:" + M4SchemaContract.VIGENTE + "}") String contrato) {
        return () -> verificar(contrato);
    }

    static void verificar(String contrato) {
        if (!PERMITIDOS.contains(contrato)) {
            throw new IllegalStateException(CODIGO + ": las fases académicas requieren uno de los contratos QA "
                    + PERMITIDOS + " (V017 y V018 candidatas); contrato declarado: " + contrato);
        }
    }
}
