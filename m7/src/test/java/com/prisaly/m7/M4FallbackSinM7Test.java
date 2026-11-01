package com.prisaly.m7;

import static org.junit.jupiter.api.Assertions.*;

import com.prisaly.m4.application.port.NotificacionPort;
import com.prisaly.m4.domain.rule.ReglaNegocioException;
import com.prisaly.m4.infrastructure.config.M4ProviderAutoConfiguration;
import com.prisaly.m7.infrastructure.NotificacionM4Adapter;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

/** Sin {@code M7Configuration}, M4 conserva su proveedor de indisponibilidad. */
class M4FallbackSinM7Test {

    @Test
    void sinM7SeRegistraElFallbackDeM4() {
        new ApplicationContextRunner()
                .withConfiguration(AutoConfigurations.of(M4ProviderAutoConfiguration.class))
                .run(contexto -> {
                    assertEquals(1, contexto.getBeansOfType(NotificacionPort.class).size());
                    assertTrue(contexto.containsBean("notificacionesNoDisponibles"));
                    NotificacionPort proveedor = contexto.getBean(NotificacionPort.class);
                    assertFalse(proveedor instanceof NotificacionM4Adapter);
                    var rechazo = assertThrows(
                            ReglaNegocioException.class,
                            () -> proveedor.notificarConvocatoria(UUID.randomUUID(), UUID.randomUUID()));
                    assertEquals("M4-PROVEEDOR-NO-DISPONIBLE", rechazo.getCodigo());
                });
    }
}
