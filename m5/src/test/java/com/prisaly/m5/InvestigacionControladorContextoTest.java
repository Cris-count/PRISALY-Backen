package com.prisaly.m5;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import com.prisaly.m5.application.ReferenciaIntento;
import com.prisaly.m5.application.acceso.AccesoProtegidoParticipacion;
import com.prisaly.m5.application.port.PortInvestigacion;
import com.prisaly.m5.application.servicio.InvestigacionServicio;
import com.prisaly.m5.presentation.rest.InvestigacionM5Controlador;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.core.env.MapPropertySource;

/**
 * DC-111-CIERRE: con el servicio de investigación registrado como bean (como hace ConfiguracionParticipacion), Spring
 * construye el controlador a través del contrato {@code InvestigacionProtegida} y cada operación pasa primero por el
 * proxy de acceso protegido.
 */
class InvestigacionControladorContextoTest {

    @Test
    void springInyectaElContratoProtegidoYCadaOperacionPasaPorElProxy() {
        AccesoProtegidoParticipacion acceso = mock(AccesoProtegidoParticipacion.class);
        PortInvestigacion port = mock(PortInvestigacion.class);
        try (var ctx = new AnnotationConfigApplicationContext()) {
            ctx.getEnvironment().getPropertySources().addFirst(
                    new MapPropertySource("dc111", Map.of(InvestigacionM5Controlador.PROPIEDAD, "true")));
            ctx.registerBean(InvestigacionServicio.class, () -> new InvestigacionServicio(acceso, port));
            ctx.registerBean(InvestigacionM5Controlador.class);
            ctx.refresh();
            var controlador = ctx.getBean(InvestigacionM5Controlador.class);
            UUID intento = UUID.randomUUID();
            verifyNoInteractions(acceso);
            assertEquals(200, controlador.listarNotas(intento).getStatusCode().value());
            verify(acceso).consultarIntento(new ReferenciaIntento(intento.toString()));
            verify(port).notasDeIntento(any(UUID.class));
        }
    }

    @Test
    void sinElInterruptorElControladorNoExiste() {
        try (var ctx = new AnnotationConfigApplicationContext()) {
            ctx.registerBean(InvestigacionServicio.class,
                    () -> new InvestigacionServicio(mock(AccesoProtegidoParticipacion.class), mock(PortInvestigacion.class)));
            ctx.registerBean(InvestigacionM5Controlador.class);
            ctx.refresh();
            assertTrue(ctx.getBeansOfType(InvestigacionM5Controlador.class).isEmpty());
        }
    }
}
