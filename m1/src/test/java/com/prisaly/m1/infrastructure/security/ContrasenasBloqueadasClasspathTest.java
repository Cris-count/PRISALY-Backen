package com.prisaly.m1.infrastructure.security;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.HexFormat;
import org.junit.jupiter.api.Test;

/** DM1-08 (DC-M1-04): the blocklist in force is verifiable by its SHA-256. */
class ContrasenasBloqueadasClasspathTest {

    @Test
    void cargaLaListaYExponeSuHuella() throws Exception {
        var lista = new ContrasenasBloqueadasClasspath();
        assertTrue(lista.tamano() >= 40);
        assertTrue(lista.contiene("password1234"));
        assertTrue(lista.contiene("administrador"));
        assertFalse(lista.contiene("# dm1-08 (dc-m1-04): lista de bloqueo de contraseñas comunes o previsibles."));
        Path fuente = Path.of("src/main/resources", ContrasenasBloqueadasClasspath.RECURSO);
        if (!Files.exists(fuente)) {
            fuente = Path.of("m1/src/main/resources", ContrasenasBloqueadasClasspath.RECURSO);
        }
        assertEquals(HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(fuente))),
                lista.huellaSha256());
    }
}
