package com.prisaly.m1.domain;

import static com.prisaly.m1.application.support.ClavesDePrueba.especificacion;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.prisaly.m1.domain.student.ClavesAccesoEstudiante;
import com.prisaly.m1.domain.student.ConjuntoClavesHmac;
import java.util.Base64;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/** D-B5: versioned keys of at least 32 random bytes, independent K-COD/K-SES, no key material in messages. */
class ConjuntoClavesHmacTest {

    @ParameterizedTest
    @ValueSource(strings = {"", "   ", "sinkid", ":QUJD", "kid con espacio:QUJD", "kid:%%%no-base64%%%"})
    void especificacionesMalFormadasSeRechazan(String especificacion) {
        assertThrows(IllegalArgumentException.class, () -> ConjuntoClavesHmac.leer("K-COD", especificacion));
    }

    @Test
    void laClaveDe16BytesDelDisenoAnteriorYAhoraEsInsuficiente() {
        var ex = assertThrows(IllegalArgumentException.class,
                () -> ConjuntoClavesHmac.leer("K-COD", especificacion("v1", 16)));
        assertTrue(ex.getMessage().contains("32 bytes"));
        assertThrows(IllegalArgumentException.class, () -> ConjuntoClavesHmac.leer("K-COD", especificacion("v1", 31)));
    }

    @Test
    void elMensajeDeErrorNuncaContieneMaterial() {
        String corta = especificacion("v1", 20);
        var ex = assertThrows(IllegalArgumentException.class, () -> ConjuntoClavesHmac.leer("K-COD", corta));
        assertFalse(ex.getMessage().contains(corta.substring(3)));
    }

    @Test
    void versionesRepetidasOIgualesSeRechazan() {
        String a = especificacion("v1", 32);
        assertThrows(IllegalArgumentException.class, () -> ConjuntoClavesHmac.leer("K", a + "," + a));
        String mismoMaterial = "v2" + a.substring(2);
        assertThrows(IllegalArgumentException.class, () -> ConjuntoClavesHmac.leer("K", a + "," + mismoMaterial));
    }

    @Test
    void firmaConLaActualYVerificaConLaAnteriorDuranteLaRotacion() {
        String anterior = especificacion("2026s1", 32);
        var soloAnterior = ConjuntoClavesHmac.leer("K-SES", anterior);
        var rotada = ConjuntoClavesHmac.leer("K-SES", especificacion("2026s2", 32) + "," + anterior);
        byte[] firmadaAntes = soloAnterior.firmar("sesion:t");
        assertEquals("2026s2", rotada.kidActual());
        assertArrayEquals(firmadaAntes, rotada.firmarCon("2026s1", "sesion:t").orElseThrow());
        assertEquals(2, rotada.firmasCandidatas("sesion:t").size());
        assertArrayEquals(firmadaAntes, rotada.firmasCandidatas("sesion:t").get(1));
        assertTrue(rotada.firmarCon("retirada", "x").isEmpty());
    }

    @Test
    void kCodYKSesDebenSerIndependientesYToStringNoExponeMaterial() {
        String compartida = especificacion("v1", 32);
        assertThrows(IllegalArgumentException.class, () -> new ClavesAccesoEstudiante(
                ConjuntoClavesHmac.leer("K-COD", compartida), ConjuntoClavesHmac.leer("K-SES", "otra" + compartida.substring(2))));
        var c = ConjuntoClavesHmac.leer("K-COD", compartida);
        String b64 = compartida.substring(3);
        assertFalse(c.toString().contains(b64));
        assertFalse(c.toString().contains(new String(Base64.getDecoder().decode(b64), java.nio.charset.StandardCharsets.ISO_8859_1)));
    }
}
