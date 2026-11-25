package com.prisaly.m7.infrastructure;

import static org.junit.jupiter.api.Assertions.*;

import com.prisaly.m1.application.port.EntregaActivacionDocentePort.EntregaFallidaException;
import com.prisaly.m7.domain.ClaseErrorCorreo;
import com.prisaly.m7.domain.ResultadoEnvioSmtp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;

/** D-05 (DC-77): invitation content (code, expiry, no password) and rejection handling. */
class CorreoActivacionDocenteAdapterTest {
    static final String TOKEN = "abcDEF123_-abcDEF123_-abcDEF123_-abcDEF1234";
    static final Instant EXPIRA = Instant.parse("2026-11-25T18:30:00Z");
    final List<String[]> enviados = new ArrayList<>();

    @Test
    void elCorreoLlevaElCodigoYLaCaducidadNuncaUnaContrasena() {
        var adaptador = new CorreoActivacionDocenteAdapter((de, para, asunto, texto) -> {
            enviados.add(new String[] {de, para, asunto, texto});
            return new ResultadoEnvioSmtp.Aceptado("id-1");
        }, "no-responder@prisaly.test", "");
        adaptador.entregar(UUID.randomUUID(), "docente@prisaly.test", TOKEN, EXPIRA);
        assertEquals(1, enviados.size());
        String[] m = enviados.get(0);
        assertEquals("no-responder@prisaly.test", m[0]);
        assertEquals("docente@prisaly.test", m[1]);
        assertEquals(CorreoActivacionDocenteAdapter.ASUNTO, m[2]);
        assertTrue(m[3].contains(TOKEN));
        assertTrue(m[3].contains("2026-11-25 18:30 UTC"));
        assertFalse(m[3].matches("(?is).*contrase[nñ]a\\s*:.*"), "never a password value");
        assertFalse(m[3].contains("#token="), "no link when none is configured");
    }

    @Test
    void conEnlaceElCodigoVaEnElFragmento() {
        String texto = new CorreoActivacionDocenteAdapter((a, b, c, d) -> new ResultadoEnvioSmtp.Aceptado("x"),
                "r@prisaly.test", "http://localhost:18176/activar").contenido(TOKEN, EXPIRA);
        assertTrue(texto.contains("http://localhost:18176/activar#token=" + TOKEN),
                "a fragment is not sent to servers or logged by proxies");
    }

    @Test
    void unRechazoSmtpLanzaSinRevelarElCodigo() {
        var adaptador = new CorreoActivacionDocenteAdapter(
                (a, b, c, d) -> new ResultadoEnvioSmtp.Rechazado(ClaseErrorCorreo.values()[0], "550", "rechazado"),
                "r@prisaly.test", "");
        var e = assertThrows(EntregaFallidaException.class,
                () -> adaptador.entregar(UUID.randomUUID(), "x@prisaly.test", TOKEN, EXPIRA));
        assertFalse(e.getMessage().contains(TOKEN));
        assertEquals("SMTP 550", e.getMessage());
    }

    @Test
    void laConfiguracionSoloExisteConElInterruptor() {
        ConditionalOnProperty c = ConfiguracionActivacionDocenteM7.class.getAnnotation(ConditionalOnProperty.class);
        assertEquals("prisaly.m1.activacion-docente.enabled", c.name()[0]);
        assertEquals("true", c.havingValue());
        assertFalse(c.matchIfMissing());
    }
}
