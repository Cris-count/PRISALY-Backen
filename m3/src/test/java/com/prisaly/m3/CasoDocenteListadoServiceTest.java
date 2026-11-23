package com.prisaly.m3;

import static org.junit.jupiter.api.Assertions.*;

import com.prisaly.m3.application.port.AutorizacionConsultaCasoPort;
import com.prisaly.m3.application.port.AutorizacionConsultaCasoPort.Decision;
import com.prisaly.m3.application.port.CasoAuthenticationException;
import com.prisaly.m3.application.port.CasoListadoDocentePort.FilaCaso;
import com.prisaly.m3.application.service.CasoDocenteListadoService;
import com.prisaly.m3.domain.model.CasoEstado;
import com.prisaly.m3.domain.rule.CasoDomainException;
import com.prisaly.m3.infrastructure.config.M3ListadoCasosConfig;
import com.prisaly.m3.presentation.CasoListadoController;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;

/** DC-67 candidate listing: permission first, ownership by query, per-case visibility, fail closed, switch off. */
class CasoDocenteListadoServiceTest {
    final UUID usuario = UUID.randomUUID();
    final FilaCaso visible = new FilaCaso(UUID.randomUUID(), "Visible", 600, CasoEstado.PUBLICADO, true);
    final FilaCaso oculta = new FilaCaso(UUID.randomUUID(), "Oculta", 600, CasoEstado.ARCHIVADO, false);

    static AutorizacionConsultaCasoPort politica(Decision listado, UUID visibleId) {
        return new AutorizacionConsultaCasoPort() {
            @Override public Decision verificarAcceso(UUID u, UUID c) { return Decision.AUTORIZADO; }
            @Override public Decision verificarVisibilidad(UUID u, UUID c, CasoEstado e, boolean a) {
                return c.equals(visibleId) ? Decision.AUTORIZADO : Decision.DENEGADO;
            }
            @Override public Decision verificarListado(UUID u) { return listado; }
        };
    }

    @Test
    void autorizadoDevuelveSoloLosVisiblesDelPropioDocente() {
        AtomicInteger consultas = new AtomicInteger();
        var s = new CasoDocenteListadoService(() -> usuario, politica(Decision.AUTORIZADO, visible.idCaso()), d -> {
            consultas.incrementAndGet();
            assertEquals(usuario, d, "only the current user's cases are queried");
            return List.of(visible, oculta);
        });
        var r = s.listarPropios();
        assertEquals(1, r.size());
        assertEquals(visible.idCaso(), r.get(0).idCaso());
        assertEquals(1, consultas.get());
    }

    @Test
    void sinPermisoSeDeniegaAntesDeConsultarYNoDisponibleEsFalloTecnico() {
        AtomicInteger consultas = new AtomicInteger();
        var denegado = new CasoDocenteListadoService(() -> usuario, politica(Decision.DENEGADO, null),
                d -> { consultas.incrementAndGet(); return List.of(); });
        assertEquals("CASE_READ_DENIED", assertThrows(CasoDomainException.class, denegado::listarPropios).code());
        assertEquals(0, consultas.get(), "no repository access without permission");
        var caido = new CasoDocenteListadoService(() -> usuario, politica(Decision.NO_DISPONIBLE, null), d -> List.of());
        assertEquals("CASE_AUTHORIZATION_UNAVAILABLE", assertThrows(CasoDomainException.class, caido::listarPropios).code());
        assertThrows(CasoAuthenticationException.class,
                () -> new CasoDocenteListadoService(() -> null, politica(Decision.AUTORIZADO, null), d -> List.of()).listarPropios());
        assertEquals("CASE_READ_DENIED", assertThrows(CasoDomainException.class,
                () -> new CasoDocenteListadoService(() -> usuario, null, d -> List.of()).listarPropios()).code());
    }

    @Test
    void laPoliticaPorDefectoNoAutorizaListados() {
        AutorizacionConsultaCasoPort heredada = new AutorizacionConsultaCasoPort() {
            @Override public Decision verificarAcceso(UUID u, UUID c) { return Decision.AUTORIZADO; }
            @Override public Decision verificarVisibilidad(UUID u, UUID c, CasoEstado e, boolean a) { return Decision.AUTORIZADO; }
        };
        assertEquals(Decision.DENEGADO, heredada.verificarListado(usuario));
    }

    @Test
    void controladorYServicioSoloExistenConElInterruptor() {
        for (Class<?> tipo : List.of(M3ListadoCasosConfig.class, CasoListadoController.class)) {
            ConditionalOnProperty c = tipo.getAnnotation(ConditionalOnProperty.class);
            assertNotNull(c, tipo.getName());
            assertEquals("prisaly.m3.listado.enabled", (c.name().length > 0 ? c.name() : c.value())[0]);
            assertEquals("true", c.havingValue());
            assertFalse(c.matchIfMissing());
        }
    }
}
