package com.prisaly.config;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.prisaly.m1.domain.student.ClavesAccesoEstudiante;
import com.prisaly.m1.domain.student.ConjuntoClavesHmac;
import com.prisaly.m7.infrastructure.CifradorCodigoAccesoAesGcm;
import java.security.SecureRandom;
import java.util.Base64;
import org.junit.jupiter.api.Test;

/** D-B5: K-OUT (M7) reused as K-COD or K-SES (M1), current or previous version, makes startup fail without leaking it. */
class ClavesAccesoEstudianteIndependientesConfigTest {

    private static String material() {
        byte[] v = new byte[32];
        new SecureRandom().nextBytes(v);
        return Base64.getEncoder().encodeToString(v);
    }

    private static ClavesAccesoEstudiante m1(String cod, String ses) {
        return new ClavesAccesoEstudiante(ConjuntoClavesHmac.leer("K-COD", cod), ConjuntoClavesHmac.leer("K-SES", ses));
    }

    @Test
    void clavesIndependientesArrancan() {
        assertDoesNotThrow(() -> ClavesAccesoEstudianteIndependientesConfig.verificar(
                m1("c:" + material(), "s:" + material()), CifradorCodigoAccesoAesGcm.leer("o:" + material())));
    }

    @Test
    void kOutReutilizadaComoKCodKSesOVersionAnteriorNoArranca() {
        String compartida = material();
        var casos = new ClavesAccesoEstudiante[] {
            m1("c:" + compartida, "s:" + material()),
            m1("c:" + material(), "s:" + compartida),
            m1("c2:" + material() + ",c1:" + compartida, "s:" + material())};
        for (ClavesAccesoEstudiante claves : casos) {
            var error = assertThrows(IllegalStateException.class,
                    () -> ClavesAccesoEstudianteIndependientesConfig.verificar(claves, CifradorCodigoAccesoAesGcm.leer("o:" + compartida)));
            assertFalse(error.getMessage().contains(compartida));
        }
        // Also when the shared material is only a previous K-OUT version kept for rotation.
        assertThrows(IllegalStateException.class, () -> ClavesAccesoEstudianteIndependientesConfig.verificar(
                m1("c:" + compartida, "s:" + material()), CifradorCodigoAccesoAesGcm.leer("o2:" + material() + ",o1:" + compartida)));
    }
}
