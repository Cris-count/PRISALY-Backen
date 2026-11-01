package com.prisaly.m7;

import static org.junit.jupiter.api.Assertions.*;
import com.prisaly.m7.application.*;
import com.prisaly.m7.domain.*;
import com.prisaly.m7.infrastructure.NotificacionM4Adapter;
import com.prisaly.m4.domain.rule.ReglaNegocioException;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.support.TransactionSynchronizationManager;

class M7ObserverTest {
    private final ConvocatoriaNotificable evento = new ConvocatoriaNotificable(
            UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID());

    @Test void identidadEstableYDestinatarioDistinto() {
        assertEquals("convocatoria:" + evento.convocatoriaId(), evento.claveEvento());
        assertTrue(evento.claveEvento().length() <= 150);
        assertNotEquals(evento.estudianteId(), evento.usuarioDestino());
        assertEquals(evento.claveEvento(), new ConvocatoriaNotificable(evento.convocatoriaId(),
                evento.sesionId(), evento.estudianteId(), evento.usuarioDestino()).claveEvento());
    }

    @Test void camposObligatorios() {
        for (int i = 0; i < 4; i++) {
            UUID[] campos = {evento.convocatoriaId(), evento.sesionId(), evento.estudianteId(), evento.usuarioDestino()};
            campos[i] = null;
            assertThrows(NullPointerException.class,
                    () -> new ConvocatoriaNotificable(campos[0], campos[1], campos[2], campos[3]));
        }
        assertThrows(IllegalArgumentException.class, () -> new AvisoConvocatoria(evento, " "));
    }

    @Test void registroEsSincronoYPrevioAExtensiones() {
        List<String> efectos = new ArrayList<>();
        var registro = new ObservadorRegistroAviso(aviso -> {
            assertEquals(evento, aviso.evento());
            assertEquals("Sesión " + evento.sesionId(), aviso.contenido());
            efectos.add("registro");
        }, "Sesión {sesionId}");
        var extensiones = new ArrayList<ObservadorConvocatoria>();
        extensiones.add(e -> efectos.add("extension"));
        var publicador = new PublicadorConvocatoria(registro, extensiones);
        extensiones.clear(); // El publicador conserva un inventario inmutable.
        publicador.publicar(evento);
        assertEquals(List.of("registro", "extension"), efectos);
    }

    @Test void faltaDeObservadorNoEsExito() {
        assertThrows(IllegalStateException.class, () -> new PublicadorConvocatoria(null, List.of()));
    }

    @Test void falloDeRegistroSePropagaSinEjecutarExtensiones() {
        var fallo = new IllegalStateException("fallo");
        var llamadas = new AtomicInteger();
        var publicador = new PublicadorConvocatoria(
                new ObservadorRegistroAviso(a -> { throw fallo; }, "Aviso"),
                List.of(e -> llamadas.incrementAndGet()));
        assertSame(fallo, assertThrows(IllegalStateException.class, () -> publicador.publicar(evento)));
        assertEquals(0, llamadas.get());
    }

    @Test void falloPosteriorNoSeOculta() {
        var llamadas = new AtomicInteger();
        var publicador = new PublicadorConvocatoria(
                new ObservadorRegistroAviso(a -> llamadas.incrementAndGet(), "Aviso"),
                List.of(e -> { throw new IllegalStateException("posterior"); }));
        assertThrows(IllegalStateException.class, () -> publicador.publicar(evento));
        assertEquals(1, llamadas.get()); // El rollback se demuestra en PostgreSQL, no aquí.
    }

    @Test void adapterResuelveContextoYRegistraSinAutenticarPorUuid() {
        List<AvisoConvocatoria> avisos = new ArrayList<>();
        var adapter = new NotificacionM4Adapter((s, e) -> {
            assertEquals(evento.sesionId(), s);
            assertEquals(evento.estudianteId(), e);
            return evento;
        }, new PublicadorConvocatoria(new ObservadorRegistroAviso(avisos::add, "Convocatoria"), List.of()));
        enTransaccion(() -> adapter.notificarConvocatoria(evento.sesionId(), evento.estudianteId()));
        assertEquals(evento.usuarioDestino(), avisos.getFirst().evento().usuarioDestino());
    }

    @Test void adapterDeniegaSinTransaccionYNoConsulta() {
        var adapter = new NotificacionM4Adapter((s, e) -> { fail("No debe consultar"); return null; },
                new PublicadorConvocatoria(new ObservadorRegistroAviso(a -> fail(), "Aviso"), List.of()));
        assertEquals("M4-PROVEEDOR-NO-DISPONIBLE", assertThrows(ReglaNegocioException.class,
                () -> adapter.notificarConvocatoria(evento.sesionId(), evento.estudianteId())).getCodigo());
    }

    @Test void adapterNoFiltraErroresDelProveedor() {
        var adapter = new NotificacionM4Adapter((s, e) -> { throw new IllegalStateException("SQL secreto"); },
                new PublicadorConvocatoria(new ObservadorRegistroAviso(a -> fail(), "Aviso"), List.of()));
        enTransaccion(() -> {
            var error = assertThrows(ReglaNegocioException.class,
                    () -> adapter.notificarConvocatoria(evento.sesionId(), evento.estudianteId()));
            assertFalse(error.getMessage().contains("SQL"));
        });
    }

    @Test void contextoAjenoNoProduceAviso() {
        var adapter = new NotificacionM4Adapter((s, e) -> evento,
                new PublicadorConvocatoria(new ObservadorRegistroAviso(a -> fail(), "Aviso"), List.of()));
        enTransaccion(() -> assertThrows(ReglaNegocioException.class,
                () -> adapter.notificarConvocatoria(UUID.randomUUID(), evento.estudianteId())));
    }

    private void enTransaccion(Runnable accion) {
        // Solo unidad: no afirma atomicidad real.
        TransactionSynchronizationManager.setActualTransactionActive(true);
        try { accion.run(); }
        finally { TransactionSynchronizationManager.clear(); }
    }
}
