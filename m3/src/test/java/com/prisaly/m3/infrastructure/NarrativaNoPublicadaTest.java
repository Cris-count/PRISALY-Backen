package com.prisaly.m3.infrastructure;

import static org.junit.jupiter.api.Assertions.*;

import com.prisaly.m3.infrastructure.config.M3NarrativaConfig;
import com.prisaly.m3.infrastructure.persistence.jdbc.GuionNarrativoJdbcAdapter;
import com.prisaly.m3.presentation.CasoNarrativaController;
import com.prisaly.m3.presentation.CasoNarrativaExceptionHandler;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;

/** DC-BE-SIM-INCREMENTO-A-15: nothing narrative is published unless the explicit switch is on (shipped value: false). */
class NarrativaNoPublicadaTest {

    @Test
    void todoElEnsamblajeNarrativoDependeDelMismoInterruptorSinValorPorDefecto() {
        for (Class<?> tipo : List.of(M3NarrativaConfig.class, CasoNarrativaController.class, CasoNarrativaExceptionHandler.class)) {
            var c = tipo.getAnnotation(ConditionalOnProperty.class);
            assertNotNull(c, tipo + " would be registered unconditionally");
            assertArrayEquals(new String[] {M3NarrativaConfig.PROPIEDAD}, c.name(), tipo.toString());
            assertEquals("true", c.havingValue());
            assertFalse(c.matchIfMissing(), "a missing property must mean OFF");
        }
    }

    @Test
    void elAdaptadorYElPuertoNoSonComponentesEscaneados() throws Exception {
        var scanner = new ClassPathScanningCandidateComponentProvider(true);
        for (var d : scanner.findCandidateComponents("com.prisaly.m3")) {
            Class<?> tipo = Class.forName(d.getBeanClassName());
            assertFalse(GuionNarrativoJdbcAdapter.class.isAssignableFrom(tipo), tipo.toString());
            assertFalse(com.prisaly.m3.api.GuionNarrativoEstudiantilPort.class.isAssignableFrom(tipo), tipo.toString());
        }
    }

    @Test
    void laConfiguracionEntregadaLoMantieneDesactivadoYV017NoEstaEnElFlywayProductivo() throws Exception {
        Path raiz = Path.of("../app/src/main/resources");
        if (!Files.exists(raiz)) { raiz = Path.of("app/src/main/resources"); }
        String yml = Files.readString(raiz.resolve("application.yml")).replace("\r", "");
        assertTrue(yml.contains("narrativa:\n      publicacion:\n        enabled: false"), "the shipped switch must be false");
        assertTrue(Files.exists(raiz.resolve("db/sim-propuesta/V017__sim_guion_narrativo.sql")));
        try (var productivas = Files.list(raiz.resolve("db/migration"))) {
            assertTrue(productivas.noneMatch(p -> p.getFileName().toString().startsWith("V017")), "V017 must not be productive");
        }
    }
}
