package com.prisaly.config;

import com.prisaly.m1.domain.student.ClavesAccesoEstudiante;
import com.prisaly.m7.infrastructure.CifradorCodigoAccesoAesGcm;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.List;
import org.springframework.beans.factory.SmartInitializingSingleton;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * D-B5 (DC-DD02-B4-B5-01): K-COD, K-SES (M1) and K-OUT (M7) must be independent. The modules cannot see each other's
 * keys (only published contracts cross module boundaries), so the APP verifies it at startup: a shared key makes the
 * context fail (fail closed). No key material is logged.
 */
@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(name = "prisaly.m1.student-access.enabled", havingValue = "true")
public class ClavesAccesoEstudianteIndependientesConfig {

    @Bean
    SmartInitializingSingleton verificarClavesAccesoEstudianteIndependientes(ClavesAccesoEstudiante claves,
            CifradorCodigoAccesoAesGcm cifrador) {
        return () -> verificar(claves, cifrador);
    }

    static void verificar(ClavesAccesoEstudiante claves, CifradorCodigoAccesoAesGcm cifrador) {
        List<byte[]> m1 = new ArrayList<>(claves.codigo().materialParaComparar());
        m1.addAll(claves.sesion().materialParaComparar());
        for (byte[] salida : cifrador.materialParaComparar()) {
            for (byte[] otra : m1) {
                if (MessageDigest.isEqual(salida, otra)) {
                    throw new IllegalStateException("K-OUT debe ser independiente de K-COD y K-SES");
                }
            }
        }
    }
}
