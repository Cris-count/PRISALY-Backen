package com.prisaly.m7;

import static org.junit.jupiter.api.Assertions.*;

import com.prisaly.m7.application.PoliticaReintentos;
import com.prisaly.m7.application.ProcesarEnviosPendientesService;
import com.prisaly.m7.application.ProveedorCorreoPort;
import com.prisaly.m7.application.ReclamoEnvioPort;
import com.prisaly.m7.domain.ClaseErrorCorreo;
import com.prisaly.m7.domain.EnvioReclamado;
import com.prisaly.m7.domain.EstadoEnvioCorreo;
import com.prisaly.m7.domain.ResultadoEnvioSmtp;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

class ProcesarEnviosPendientesServiceTest {

    @Test
    void aceptacionRegistraEnviado() {
        var reclamo = new ReclamoFake();
        reclamo.cola.add(reclamado(1));
        var servicio = new ProcesarEnviosPendientesService(
                reclamo,
                (r, d, a, c) -> new ResultadoEnvioSmtp.Aceptado("<id@test>"),
                PoliticaReintentos.ratificada(),
                "noreply@prisaly.test",
                10,
                Duration.ofSeconds(30));
        assertEquals(1, servicio.procesarLote());
        assertEquals(EstadoEnvioCorreo.ENVIADO, reclamo.ultimoEstado.get());
    }

    @Test
    void permanenteMarcaFallido() {
        var reclamo = new ReclamoFake();
        reclamo.cola.add(reclamado(1));
        var servicio = new ProcesarEnviosPendientesService(
                reclamo,
                (r, d, a, c) -> new ResultadoEnvioSmtp.Rechazado(
                        ClaseErrorCorreo.PERMANENTE_DESTINATARIO, "550", "User unknown"),
                PoliticaReintentos.ratificada(),
                "noreply@prisaly.test",
                10,
                Duration.ofSeconds(30));
        servicio.procesarLote();
        assertEquals(EstadoEnvioCorreo.FALLIDO, reclamo.ultimoEstado.get());
    }

    @Test
    void configuracionNoMarcaFallido() {
        var reclamo = new ReclamoFake();
        reclamo.cola.add(reclamado(1));
        var servicio = new ProcesarEnviosPendientesService(
                reclamo,
                (r, d, a, c) -> new ResultadoEnvioSmtp.Rechazado(
                        ClaseErrorCorreo.CONFIGURACION, "535", "Authentication failed"),
                PoliticaReintentos.ratificada(),
                "noreply@prisaly.test",
                10,
                Duration.ofSeconds(30));
        servicio.procesarLote();
        assertEquals(EstadoEnvioCorreo.PENDIENTE, reclamo.ultimoEstado.get());
        assertEquals(Duration.ofMinutes(1), reclamo.ultimaEspera.get());
    }

    @Test
    void quintoIntentoTransitorioMarcaFallido() {
        var reclamo = new ReclamoFake();
        reclamo.cola.add(reclamado(5));
        var servicio = new ProcesarEnviosPendientesService(
                reclamo,
                (r, d, a, c) -> new ResultadoEnvioSmtp.Rechazado(
                        ClaseErrorCorreo.TRANSITORIO, "421", "timeout"),
                PoliticaReintentos.ratificada(),
                "noreply@prisaly.test",
                10,
                Duration.ofSeconds(30));
        servicio.procesarLote();
        assertEquals(EstadoEnvioCorreo.FALLIDO, reclamo.ultimoEstado.get());
    }

    private static EnvioReclamado reclamado(int intentos) {
        return new EnvioReclamado(
                UUID.randomUUID(),
                "dest@example.invalid",
                "Asunto",
                "Cuerpo",
                intentos,
                Instant.parse("2030-01-01T00:00:30Z"));
    }

    private static final class ReclamoFake implements ReclamoEnvioPort {
        final List<EnvioReclamado> cola = new ArrayList<>();
        final AtomicReference<String> ultimoEstado = new AtomicReference<>();
        final AtomicReference<Duration> ultimaEspera = new AtomicReference<>();

        @Override
        public List<EnvioReclamado> reclamar(int lote, Duration lease, int maxIntentos) {
            List<EnvioReclamado> loteReclamado = List.copyOf(cola);
            cola.clear();
            return loteReclamado;
        }

        @Override
        public boolean registrarAceptacion(UUID idEnvio, InstantLease lease, String messageId) {
            ultimoEstado.set(EstadoEnvioCorreo.ENVIADO);
            return true;
        }

        @Override
        public boolean registrarFallo(
                UUID idEnvio, InstantLease lease, String estadoFinal, String ultimoError, Duration proximoEn) {
            ultimoEstado.set(estadoFinal);
            ultimaEspera.set(proximoEn);
            return true;
        }
    }
}
