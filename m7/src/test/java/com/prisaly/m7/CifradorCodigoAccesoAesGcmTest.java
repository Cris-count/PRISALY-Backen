package com.prisaly.m7;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.prisaly.m7.infrastructure.CifradorCodigoAccesoAesGcm;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HashSet;
import java.util.Set;
import org.junit.jupiter.api.Test;

/** D-B2.7 / D-B5: AES-256-GCM, unique nonce, authenticated challenge, versioned K-OUT, no material exposed. */
class CifradorCodigoAccesoAesGcmTest {

    private static String clave(String kid, int bytes) {
        byte[] v = new byte[bytes];
        new SecureRandom().nextBytes(v);
        return kid + ":" + Base64.getEncoder().encodeToString(v);
    }

    @Test
    void cifraYDescifraSinDejarElCodigoNiElCorreoEnClaro() {
        var c = CifradorCodigoAccesoAesGcm.leer(clave("out-1", 32));
        var cifrado = c.cifrar("ch-1", "est@prisaly.test", "12345678");
        String bruto = new String(cifrado.carga(), StandardCharsets.ISO_8859_1);
        assertFalse(bruto.contains("12345678") || bruto.contains("est@prisaly.test"));
        assertEquals(12, cifrado.nonce().length);
        assertEquals("out-1", cifrado.kid());
        var contenido = c.descifrar("ch-1", cifrado.carga(), cifrado.nonce(), cifrado.kid()).orElseThrow();
        assertEquals("est@prisaly.test", contenido.destinatario());
        assertEquals("12345678", contenido.codigo());
        assertFalse(contenido.toString().contains("12345678"));
    }

    @Test
    void elNonceEsUnicoPorMensaje() {
        var c = CifradorCodigoAccesoAesGcm.leer(clave("out-1", 32));
        Set<String> nonces = new HashSet<>();
        for (int i = 0; i < 500; i++) {
            nonces.add(Base64.getEncoder().encodeToString(c.cifrar("ch", "a@b.test", "00000000").nonce()));
        }
        assertEquals(500, nonces.size());
    }

    @Test
    void manipulacionOtroDesafioOKidRetiradoNoDescifran() {
        String anterior = clave("2026s1", 32);
        var viejo = CifradorCodigoAccesoAesGcm.leer(anterior);
        var cifrado = viejo.cifrar("ch-1", "a@b.test", "12345678");
        byte[] alterado = cifrado.carga().clone();
        alterado[0] ^= 1;
        assertTrue(viejo.descifrar("ch-1", alterado, cifrado.nonce(), "2026s1").isEmpty());
        assertTrue(viejo.descifrar("ch-OTRO", cifrado.carga(), cifrado.nonce(), "2026s1").isEmpty(), "AAD binds the row");
        // Rotation: the new key encrypts, the previous one still decrypts; once retired it no longer does.
        var rotado = CifradorCodigoAccesoAesGcm.leer(clave("2026s2", 32) + "," + anterior);
        assertEquals("2026s2", rotado.kidActual());
        assertTrue(rotado.descifrar("ch-1", cifrado.carga(), cifrado.nonce(), "2026s1").isPresent());
        var retirado = CifradorCodigoAccesoAesGcm.leer(clave("2026s2", 32));
        assertTrue(retirado.descifrar("ch-1", cifrado.carga(), cifrado.nonce(), "2026s1").isEmpty());
    }

    @Test
    void claveAusenteCortaLargaOMalFormadaNoArrancaYNoMuestraMaterial() {
        for (String mala : new String[] {null, "", "sinkid", "k:%%%", clave("k", 16), clave("k", 31), clave("k", 33)}) {
            assertThrows(IllegalArgumentException.class, () -> CifradorCodigoAccesoAesGcm.leer(mala));
        }
        String repetida = clave("a", 32);
        assertThrows(IllegalArgumentException.class, () -> CifradorCodigoAccesoAesGcm.leer(repetida + ",b" + repetida.substring(1)));
        String valida = clave("out-1", 32);
        assertFalse(CifradorCodigoAccesoAesGcm.leer(valida).toString().contains(valida.substring(6)));
    }
}
