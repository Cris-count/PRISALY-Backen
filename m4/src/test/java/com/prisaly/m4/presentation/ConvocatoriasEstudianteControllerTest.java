package com.prisaly.m4.presentation;

import static org.junit.jupiter.api.Assertions.*;

import com.prisaly.m4.api.ConsultaProgramacionNoDisponibleException;
import com.prisaly.m4.api.ContextoProgramacion;
import com.prisaly.m4.api.ProgramacionAutenticadaPort;
import com.prisaly.m4.presentation.controller.ConvocatoriasEstudianteController;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;

/** CIERRE-FUNCIONAL-09 F2: discovery of the student's own authorized convocations; no client-supplied student id. */
class ConvocatoriasEstudianteControllerTest {
    static final Instant T = Instant.parse("2026-11-13T14:00:00Z");
    final UUID estudiante = UUID.randomUUID();

    ContextoProgramacion contexto(String autorizacion, Optional<Instant> retiro) {
        return new ContextoProgramacion(UUID.randomUUID(), estudiante, UUID.randomUUID(), UUID.randomUUID(), "EnCurso",
                autorizacion, retiro, "Publicado", true, T, T.plusSeconds(3600), Optional.of(T), Optional.empty(),
                Duration.ofMinutes(30), Duration.ofMinutes(10), T);
    }

    ConvocatoriasEstudianteController controlador(ProgramacionAutenticadaPort puerto) {
        var fabrica = new DefaultListableBeanFactory();
        if (puerto != null) { fabrica.registerSingleton("programacion", puerto); }
        return new ConvocatoriasEstudianteController(fabrica.getBeanProvider(ProgramacionAutenticadaPort.class));
    }

    ProgramacionAutenticadaPort devuelve(List<ContextoProgramacion> lista) {
        return new ProgramacionAutenticadaPort() {
            public Optional<ContextoProgramacion> consultarSesion(UUID s) { throw new AssertionError(); }
            public Optional<ContextoProgramacion> consultarConvocatoria(UUID c) { throw new AssertionError(); }
            public List<ContextoProgramacion> listarConvocatoriasPropias() { return lista; }
        };
    }

    @Test
    void soloDevuelveLasAutorizadasSinRetiroYSinCache() {
        var autorizada = contexto("Autorizado", Optional.empty());
        var retirada = contexto("Retirado", Optional.of(T));
        var conRetiro = contexto("Autorizado", Optional.of(T));
        var r = controlador(devuelve(List.of(autorizada, retirada, conRetiro))).listar();
        assertEquals(200, r.getStatusCode().value());
        assertTrue(r.getHeaders().getCacheControl().contains("no-store"));
        assertEquals(1, r.getBody().size());
        var dto = r.getBody().get(0);
        assertEquals(autorizada.convocatoriaId(), dto.idConvocatoria());
        assertEquals(autorizada.sesionId(), dto.idSesion());
        assertEquals(1800, dto.tiempoMaximoParticipacionSegundos());
        assertEquals(T, dto.fechaInicioReal());
        assertNull(dto.fechaFinReal());
    }

    @Test
    void sinPuertoOSinSoporteFallaCerrado() {
        assertThrows(ConsultaProgramacionNoDisponibleException.class, () -> controlador(null).listar());
        ProgramacionAutenticadaPort antiguo = new ProgramacionAutenticadaPort() {
            public Optional<ContextoProgramacion> consultarSesion(UUID s) { return Optional.empty(); }
            public Optional<ContextoProgramacion> consultarConvocatoria(UUID c) { return Optional.empty(); }
        };
        assertThrows(ConsultaProgramacionNoDisponibleException.class, () -> controlador(antiguo).listar());
    }

    @Test
    void elEndpointNoAceptaIdentificadoresDelCliente() {
        var metodo = java.util.Arrays.stream(ConvocatoriasEstudianteController.class.getDeclaredMethods())
                .filter(m -> m.getName().equals("listar")).findFirst().orElseThrow();
        assertEquals(0, metodo.getParameterCount(), "the student comes only from the M1 session");
    }
}
