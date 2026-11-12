package com.prisaly.m7;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.prisaly.m7.application.CifradorCodigoAcceso;
import com.prisaly.m7.application.EntregaCodigoAccesoRepositorio;
import com.prisaly.m7.application.ProcesarEntregasCodigoAccesoService;
import com.prisaly.m7.domain.ClaseErrorCorreo;
import com.prisaly.m7.domain.ResultadoEnvioSmtp;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** D-B2.5: attempts at 0/+30/+90 s within validity, never with less than 60 s left, revocation on failure. */
class ProcesarEntregasCodigoAccesoServiceTest {

    private final AtomicReference<Instant> ahora = new AtomicReference<>(Instant.parse("2026-11-11T10:00:00Z"));
    private final Clock reloj = new Clock() {
        @Override public ZoneOffset getZone() { return ZoneOffset.UTC; }
        @Override public Clock withZone(ZoneId zone) { return this; }
        @Override public Instant instant() { return ahora.get(); }
    };

    /** Minimal in-memory Outbox mirroring the JDBC semantics. */
    static final class Fila {
        UUID id = UUID.randomUUID();
        String challenge = UUID.randomUUID().toString();
        String estado = "PENDIENTE";
        int intentos;
        Instant proximo;
        Instant creada;
        Instant expira;
        byte[] carga = {1};
        String error;
    }

    final Map<UUID, Fila> filas = new LinkedHashMap<>();
    final List<String> revocadas = new ArrayList<>();
    final List<String> enviados = new ArrayList<>();
    ResultadoEnvioSmtp siguiente;
    boolean descifrable = true;

    final EntregaCodigoAccesoRepositorio repositorio = new EntregaCodigoAccesoRepositorio() {
        @Override public List<EntregaReclamada> reclamar(int lote, Duration lease, Instant t) {
            return filas.values().stream().filter(f -> f.estado.equals("PENDIENTE") && !f.proximo.isAfter(t))
                    .map(f -> new EntregaReclamada(f.id, f.challenge, f.carga, new byte[12], "out-1", f.intentos, f.creada, f.expira))
                    .toList();
        }
        @Override public void marcarEnviada(UUID id, Instant t) { cerrar(id, "ENVIADA", null); }
        @Override public void reprogramar(UUID id, int intentos, Instant proximo, String error) {
            Fila f = filas.get(id); f.intentos = intentos; f.proximo = proximo; f.error = error;
        }
        @Override public void finalizarSinEnvio(UUID id, EstadoFinal e, String error, Instant t) { cerrar(id, e.name(), error); }
        @Override public int cancelarSinTiempo(Instant umbral, Instant t) {
            int n = 0;
            for (Fila f : filas.values()) {
                if (f.estado.equals("PENDIENTE") && !f.expira.isAfter(umbral)) { cerrar(f.id, "CANCELADA_VENCIDA", "SIN_TIEMPO"); n++; }
            }
            return n;
        }
        @Override public int purgarMetadatos(Instant limite) { return 0; }
        void cerrar(UUID id, String estado, String error) {
            Fila f = filas.get(id); f.estado = estado; f.carga = null; f.error = error;
        }
    };

    final CifradorCodigoAcceso cifrador = new CifradorCodigoAcceso() {
        @Override public Cifrado cifrar(String c, String d, String k) { throw new UnsupportedOperationException(); }
        @Override public Optional<Contenido> descifrar(String c, byte[] carga, byte[] nonce, String kid) {
            return descifrable ? Optional.of(new Contenido("est@prisaly.test", "87654321")) : Optional.empty();
        }
    };

    ProcesarEntregasCodigoAccesoService servicio;

    @BeforeEach
    void preparar() {
        servicio = new ProcesarEntregasCodigoAccesoService(repositorio, cifrador, (rem, dest, asunto, cuerpo) -> {
            enviados.add(dest + "|" + asunto + "|" + cuerpo);
            return siguiente;
        }, revocadas::add, "no-reply@prisaly.test", 10, reloj);
    }

    private Fila fila() {
        Fila f = new Fila();
        f.creada = ahora.get();
        f.proximo = ahora.get();
        f.expira = ahora.get().plus(Duration.ofMinutes(5));
        filas.put(f.id, f);
        return f;
    }

    private void avanzar(Duration d) {
        ahora.set(ahora.get().plus(d));
    }

    @Test
    void entregaAceptadaBorraLaCargaYElCorreoSoloLlevaElCodigoEnElCuerpo() {
        Fila f = fila();
        siguiente = new ResultadoEnvioSmtp.Aceptado("id-1");
        assertEquals(1, servicio.procesar());
        assertEquals("ENVIADA", f.estado);
        assertEquals(null, f.carga);
        String correo = enviados.get(0);
        assertTrue(correo.startsWith("est@prisaly.test|PRISALY — Código de acceso|"));
        assertFalse(correo.split("\\|")[1].contains("87654321"), "never in the subject");
        assertTrue(correo.contains("87654321") && correo.contains("5 minutos") && !correo.contains(f.challenge));
        assertTrue(revocadas.isEmpty());
    }

    @Test
    void fallosTransitoriosReprogramanA30Y90sYElTerceroAgotaYRevoca() {
        Fila f = fila();
        siguiente = new ResultadoEnvioSmtp.Rechazado(ClaseErrorCorreo.TRANSITORIO, "451", "temporal");
        servicio.procesar();
        assertEquals(1, f.intentos);
        assertEquals(f.creada.plusSeconds(30), f.proximo);
        avanzar(Duration.ofSeconds(30));
        servicio.procesar();
        assertEquals(2, f.intentos);
        assertEquals(f.creada.plusSeconds(90), f.proximo);
        avanzar(Duration.ofSeconds(60));
        servicio.procesar();
        assertEquals("AGOTADA", f.estado);
        assertEquals(null, f.carga);
        assertEquals(List.of(f.challenge), revocadas);
        assertEquals(3, enviados.size());
    }

    @Test
    void falloPermanenteAnulaElCodigoSinReintentar() {
        Fila f = fila();
        siguiente = new ResultadoEnvioSmtp.Rechazado(ClaseErrorCorreo.PERMANENTE_DESTINATARIO, "550", "no existe");
        servicio.procesar();
        assertEquals("FALLIDA_PERMANENTE", f.estado);
        assertEquals("SMTP_550", f.error);
        assertEquals(null, f.carga);
        assertEquals(List.of(f.challenge), revocadas);
        avanzar(Duration.ofSeconds(30));
        servicio.procesar();
        assertEquals(1, enviados.size());
    }

    @Test
    void nuncaSeEnviaConMenosDe60sDeVigenciaYLaCargaSeBorra() {
        Fila f = fila();
        avanzar(Duration.ofMinutes(4).plusSeconds(1));
        siguiente = new ResultadoEnvioSmtp.Aceptado("x");
        servicio.procesar();
        assertEquals("CANCELADA_VENCIDA", f.estado);
        assertEquals(null, f.carga);
        assertTrue(enviados.isEmpty());
    }

    @Test
    void unReintentoQueDejariaMenosDe60sSeCancela() {
        Fila f = fila();
        f.expira = f.creada.plusSeconds(140);
        siguiente = new ResultadoEnvioSmtp.Rechazado(ClaseErrorCorreo.TRANSITORIO, "421", "x");
        servicio.procesar();
        avanzar(Duration.ofSeconds(30));
        servicio.procesar();
        // The +90 s attempt would leave 50 s: cancelled and revoked instead of rescheduled.
        assertEquals("CANCELADA_VENCIDA", f.estado);
        assertEquals(List.of(f.challenge), revocadas);
    }

    @Test
    void cargaIndescifrableNoSeEnviaYAnulaElCodigo() {
        Fila f = fila();
        descifrable = false;
        servicio.procesar();
        assertEquals("FALLIDA_PERMANENTE", f.estado);
        assertEquals("DESCIFRADO", f.error);
        assertTrue(enviados.isEmpty());
        assertEquals(List.of(f.challenge), revocadas);
    }
}
