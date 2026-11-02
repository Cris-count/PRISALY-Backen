package com.prisaly.m7;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.prisaly.m7.infrastructure.M7WorkerConfiguration;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

class M7WorkerDisabledTest {

    @Test
    void workerNoSeRegistraSinPropiedad() {
        new ApplicationContextRunner()
                .withUserConfiguration(M7WorkerConfiguration.class)
                .run(contexto -> assertFalse(contexto.containsBean("procesarEnviosPendientesM7")));
    }

    @Test
    void workerExigePropiedadesAlHabilitarse() {
        new ApplicationContextRunner()
                .withUserConfiguration(M7WorkerConfiguration.class)
                .withPropertyValues("prisaly.m7.worker.enabled=true")
                .run(contexto -> assertTrue(contexto.getStartupFailure() != null));
    }
}
