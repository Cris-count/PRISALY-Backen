package com.prisaly.m1.infrastructure.activacion;

import static org.junit.jupiter.api.Assertions.*;

import com.prisaly.m1.presentation.ActivacionDocenteController;
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.mock.env.MockEnvironment;

/** D-05 (DC-77): mandatory validity without default, bounds, and OFF-by-default assembly. */
class ActivacionDocenteConfigTest {

    private static String error(String valor) {
        MockEnvironment env = new MockEnvironment();
        if (valor != null) { env.setProperty("prisaly.m1.activacion-docente.vigencia", valor); }
        return assertThrows(IllegalStateException.class, () -> ActivacionDocenteConfig.vigencia(env)).getMessage();
    }

    @Test
    void laVigenciaEsObligatoriaYAcotada() {
        assertTrue(error(null).startsWith("M1-ACTIVACION-VIGENCIA"), "no default");
        assertTrue(error("  ").startsWith("M1-ACTIVACION-VIGENCIA"));
        assertTrue(error("24 horas").startsWith("M1-ACTIVACION-VIGENCIA"));
        assertTrue(error("PT14M").startsWith("M1-ACTIVACION-VIGENCIA"));
        assertTrue(error("P8D").startsWith("M1-ACTIVACION-VIGENCIA"));
        assertTrue(error("-PT24H").startsWith("M1-ACTIVACION-VIGENCIA"));
        MockEnvironment env = new MockEnvironment().withProperty("prisaly.m1.activacion-docente.vigencia", "PT24H");
        assertEquals(Duration.ofHours(24), ActivacionDocenteConfig.vigencia(env));
        env.setProperty("prisaly.m1.activacion-docente.vigencia", "PT15M");
        assertEquals(Duration.ofMinutes(15), ActivacionDocenteConfig.vigencia(env));
        env.setProperty("prisaly.m1.activacion-docente.vigencia", "P7D");
        assertEquals(Duration.ofDays(7), ActivacionDocenteConfig.vigencia(env));
    }

    @Test
    void sinInterruptorNoHayBeansNiRutas() {
        for (Class<?> tipo : List.of(ActivacionDocenteConfig.class, ActivacionDocenteController.class)) {
            ConditionalOnProperty c = tipo.getAnnotation(ConditionalOnProperty.class);
            assertNotNull(c, tipo.getName());
            assertEquals("prisaly.m1.activacion-docente.enabled", (c.name().length > 0 ? c.name() : c.value())[0]);
            assertEquals("true", c.havingValue());
            assertFalse(c.matchIfMissing());
        }
    }
}
