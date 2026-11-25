package com.prisaly.config;

import com.prisaly.m4.infrastructure.config.M4SchemaContract;

import org.springframework.beans.factory.SmartInitializingSingleton;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * DD-02 B, preparación técnica (DC-CLAUDE-BE-CIERRE-07, prioridad 2). El acceso estudiantil persiste credenciales,
 * sesiones, intentos de acceso y entregas en las tablas candidatas V014-V016 ({@code db/dd02b-propuesta}), que no
 * forman parte del Flyway productivo. Con el flag activo y el contrato de esquema vigente ({@code bd0052-v013}), la
 * aplicación arrancaba y los endpoints estudiantiles fallaban después, en tiempo de ejecución, por tablas ausentes.
 *
 * <p>Esta verificación hace que el arranque falle cerrado salvo que el contrato declarado sea
 * {@link M4SchemaContract#V013_DD02B}; ese contrato, a su vez, obliga a M4 a comprobar en la base real la presencia
 * exacta de las cuatro tablas DD-02 B. No activa nada, no cambia permisos, endpoints ni migraciones, y no afecta al
 * arranque con el flag desactivado (el valor por defecto).
 */
@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(name = "prisaly.m1.student-access.enabled", havingValue = "true")
public class EsquemaAccesoEstudianteConfig {

    static final String CODIGO = "DD02-ESQUEMA-REQUERIDO";

    @Bean
    SmartInitializingSingleton verificarEsquemaAccesoEstudiante(
            @Value("${prisaly.m4.schema-contract:" + M4SchemaContract.VIGENTE + "}") String contrato) {
        return () -> verificar(contrato);
    }

    /**
     * DC-BE-SIM-B1-B2A-24: the QA-only narrative contracts that also contain V014-V016 are accepted, so a student QA
     * run can include V017/V018. DC-BE-CIERRE-DEFINITIVO-87: the integral candidate (V014-V016 + V019 + V020) as well.
     * Every accepted contract still makes M4 verify the four DD-02 B tables.
     */
    static final java.util.Set<String> PERMITIDOS = java.util.Set.of(M4SchemaContract.V013_DD02B,
            M4SchemaContract.V013_DD02B_SIM_A, M4SchemaContract.V013_DD02B_SIM_AB, M4SchemaContract.V013_INTEGRAL);

    static void verificar(String contrato) {
        if (!PERMITIDOS.contains(contrato)) {
            throw new IllegalStateException(CODIGO + ": el acceso estudiantil requiere el contrato de esquema "
                    + M4SchemaContract.V013_DD02B + " (V001-V009, V013 y V014-V016) o una de sus variantes QA "
                    + PERMITIDOS + "; contrato declarado: " + contrato);
        }
    }
}
