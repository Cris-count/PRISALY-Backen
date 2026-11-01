package com.prisaly.m7;

import static org.junit.jupiter.api.Assertions.*;

import com.prisaly.m4.domain.rule.ReglaNegocioException;
import com.prisaly.m7.application.NotificarConvocatoriaCommand;
import com.prisaly.m7.application.NotificarConvocatoriaService;
import com.prisaly.m7.application.ObservadorConvocatoria;
import com.prisaly.m7.application.ObservadorRegistroAviso;
import com.prisaly.m7.application.PublicadorConvocatoria;
import com.prisaly.m7.domain.AvisoConvocatoria;
import com.prisaly.m7.domain.ConvocatoriaNotificable;
import com.prisaly.m7.domain.EnvioCorreoConvocatoria;
import com.prisaly.m7.infrastructure.NotificacionM4Adapter;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.support.TransactionSynchronizationManager;

class M7ObserverTest {

    private final ConvocatoriaNotificable evento = new ConvocatoriaNotificable(
            UUID.randomUUID(),
            UUID.randomUUID(),
            UUID.randomUUID(),
            UUID.randomUUID(),
            "destino@example.invalid");

    @Test
    void identidadEstableYDestinatarioDistinto() {
        assertEquals("convocatoria:" + evento.convocatoriaId(), evento.claveEvento());
        assertEquals(evento.claveEvento(), evento.claveIdempotencia());
        assertTrue(evento.claveEvento().length() <= 150);
        assertNotEquals(evento.estudianteId(), evento.usuarioDestino());
    }

    @Test
    void camposObligatorios() {
        assertThrows(
                NullPointerException.class,
                () -> new ConvocatoriaNotificable(
                        null, evento.sesionId(), evento.estudianteId(), evento.usuarioDestino(), evento.correoDestino()));
        assertThrows(
                IllegalArgumentException.class,
                () -> new ConvocatoriaNotificable(
                        evento.convocatoriaId(),
                        evento.sesionId(),
                        evento.estudianteId(),
                        evento.usuarioDestino(),
                        " "));
        assertThrows(IllegalArgumentException.class, () -> new AvisoConvocatoria(evento, " "));
        assertThrows(IllegalArgumentException.class, () -> new EnvioCorreoConvocatoria(evento, " ", "c"));
    }

    @Test
    void registroEsSincronoYPrevioAExtensiones() {
        List<String> efectos = new ArrayList<>();
        var registro = new ObservadorRegistroAviso(
                aviso -> {
                    assertEquals(evento, aviso.evento());
                    efectos.add("registro");
                },
                "Sesión {sesionId}");
        var extensiones = new ArrayList<ObservadorConvocatoria>();
        extensiones.add(e -> efectos.add("extension"));
        var publicador = new PublicadorConvocatoria(registro, extensiones);
        extensiones.clear();
        publicador.publicar(evento);
        assertEquals(List.of("registro", "extension"), efectos);
    }

    @Test
    void servicioRegistraAvisoYEncolaCorreoEnOrden() {
        List<String> pasos = new ArrayList<>();
        var servicio = new NotificarConvocatoriaService(
                (s, e) -> evento,
                a -> pasos.add("aviso"),
                c -> {
                    assertEquals(EnvioCorreoConvocatoria.ESTADO_PENDIENTE, EnvioCorreoConvocatoria.ESTADO_PENDIENTE);
                    assertEquals(evento.claveIdempotencia(), c.evento().claveIdempotencia());
                    pasos.add("correo");
                },
                "Sesión {sesionId}",
                "Asunto {sesionId}");
        enTransaccion(() -> servicio.ejecutar(new NotificarConvocatoriaCommand(evento.sesionId(), evento.estudianteId())));
        assertEquals(List.of("aviso", "correo"), pasos);
    }

    @Test
    void servicioExigeTransaccionDeEscritura() {
        var servicio = new NotificarConvocatoriaService(
                (s, e) -> fail(), a -> fail(), c -> fail(), "Aviso", "Asunto");
        assertThrows(
                IllegalStateException.class,
                () -> servicio.ejecutar(new NotificarConvocatoriaCommand(evento.sesionId(), evento.estudianteId())));
    }

    @Test
    void falloDeAvisoNoEncolaCorreo() {
        var llamadas = new AtomicInteger();
        var servicio = new NotificarConvocatoriaService(
                (s, e) -> evento,
                a -> {
                    throw new IllegalStateException("aviso");
                },
                c -> llamadas.incrementAndGet(),
                "Aviso",
                "Asunto");
        enTransaccion(() -> assertThrows(
                IllegalStateException.class,
                () -> servicio.ejecutar(new NotificarConvocatoriaCommand(evento.sesionId(), evento.estudianteId()))));
        assertEquals(0, llamadas.get());
    }

    @Test
    void adapterDelegaEnServicioSinAutenticarPorUuid() {
        AtomicInteger ejecuciones = new AtomicInteger();
        var servicio = new NotificarConvocatoriaService(
                (s, e) -> {
                    assertEquals(evento.sesionId(), s);
                    assertEquals(evento.estudianteId(), e);
                    return evento;
                },
                a -> ejecuciones.incrementAndGet(),
                c -> ejecuciones.incrementAndGet(),
                "Convocatoria",
                "Asunto");
        var adapter = new NotificacionM4Adapter(servicio);
        enTransaccion(() -> adapter.notificarConvocatoria(evento.sesionId(), evento.estudianteId()));
        assertEquals(2, ejecuciones.get());
    }

    @Test
    void adapterDeniegaSinTransaccion() {
        var adapter = new NotificacionM4Adapter(new NotificarConvocatoriaService(
                (s, e) -> fail(), a -> fail(), c -> fail(), "Aviso", "Asunto"));
        assertEquals(
                "M4-PROVEEDOR-NO-DISPONIBLE",
                assertThrows(
                                ReglaNegocioException.class,
                                () -> adapter.notificarConvocatoria(evento.sesionId(), evento.estudianteId()))
                        .getCodigo());
    }

    @Test
    void adapterNoFiltraErroresDelProveedorComoSql() {
        var adapter = new NotificacionM4Adapter(new NotificarConvocatoriaService(
                (s, e) -> {
                    throw new IllegalStateException("SQL secreto");
                },
                a -> fail(),
                c -> fail(),
                "Aviso",
                "Asunto"));
        enTransaccion(() -> {
            var error = assertThrows(
                    ReglaNegocioException.class,
                    () -> adapter.notificarConvocatoria(evento.sesionId(), evento.estudianteId()));
            assertFalse(error.getMessage().contains("SQL"));
        });
    }

    private void enTransaccion(Runnable accion) {
        TransactionSynchronizationManager.setActualTransactionActive(true);
        try {
            accion.run();
        } finally {
            TransactionSynchronizationManager.clear();
        }
    }
}
