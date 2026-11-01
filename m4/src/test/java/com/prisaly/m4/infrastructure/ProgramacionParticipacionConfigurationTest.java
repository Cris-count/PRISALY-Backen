package com.prisaly.m4.infrastructure;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.jdbc.core.JdbcTemplate;

import com.prisaly.m4.api.ConsultaProgramacionNoDisponibleException;
import com.prisaly.m4.api.ProgramacionParticipacionPort;
import com.prisaly.m4.infrastructure.adapter.ProgramacionParticipacionJdbcAdapter;
import com.prisaly.m4.infrastructure.config.M4ProgramacionConsultaAutoConfiguration;

class ProgramacionParticipacionConfigurationTest {
    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(M4ProgramacionConsultaAutoConfiguration.class));

    @Test
    void apagadoPorDefectoRechazaAmbasConsultasSinConectarABaseDeDatos() {
        runner.run(context -> {
            assertNull(context.getStartupFailure());
            var port = context.getBean(ProgramacionParticipacionPort.class);
            assertThrows(ConsultaProgramacionNoDisponibleException.class,
                    () -> port.consultarSesion(UUID.randomUUID(), UUID.randomUUID()));
            assertThrows(ConsultaProgramacionNoDisponibleException.class,
                    () -> port.consultarConvocatoria(UUID.randomUUID(), UUID.randomUUID()));
        });
    }

    @Test
    void activacionExplicitaRegistraAdaptadorOficial() {
        runner.withPropertyValues("prisaly.m4.consulta-bd0052.enabled=true")
                .withBean(JdbcTemplate.class, () -> mock(JdbcTemplate.class)).run(context -> {
                    assertNull(context.getStartupFailure());
                    assertInstanceOf(ProgramacionParticipacionJdbcAdapter.class,
                            context.getBean(ProgramacionParticipacionPort.class));
                });
    }

    @Test
    void proveedorExplicitoNoSeReemplazaPorFallback() {
        var proveedor = mock(ProgramacionParticipacionPort.class);
        runner.withBean(ProgramacionParticipacionPort.class, () -> proveedor).run(context -> {
            assertNull(context.getStartupFailure());
            assertSame(proveedor, context.getBean(ProgramacionParticipacionPort.class));
        });
    }

    @Test
    void activacionSinConexionFallaAlArrancar() {
        runner.withPropertyValues("prisaly.m4.consulta-bd0052.enabled=true").run(context ->
                assertNotNull(context.getStartupFailure()));
    }
}
