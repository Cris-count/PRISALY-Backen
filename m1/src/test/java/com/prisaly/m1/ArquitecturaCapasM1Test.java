package com.prisaly.m1;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

/**
 * A17: layer guard for M1. It searches the source text, so a fully qualified name is caught
 * just like an import.
 */
class ArquitecturaCapasM1Test {

    private static final String BASE = "src/main/java/com/prisaly/m1/";

    @Test
    void capasInternasNoDependenDeInfraestructura() {
        assertSinReferencias(List.of("presentation", "application", "domain", "api"),
                "com.prisaly.m1.infrastructure");
    }

    @Test
    void dominioYApiNoDependenDeAplicacionNiPresentacion() {
        assertSinReferencias(List.of("domain", "api"), "com.prisaly.m1.application");
        assertSinReferencias(List.of("domain", "api"), "com.prisaly.m1.presentation");
    }

    @Test
    void aplicacionNoDependeDePresentacion() {
        assertSinReferencias(List.of("application"), "com.prisaly.m1.presentation");
    }

    private static void assertSinReferencias(List<String> capas, String paqueteProhibido) {
        Path raiz = raiz();
        List<String> infracciones = new ArrayList<>();
        for (String capa : capas) {
            Path dir = raiz.resolve(BASE + capa);
            assertTrue(Files.isDirectory(dir), "Falta la capa " + dir);
            try (Stream<Path> fuentes = Files.walk(dir)) {
                fuentes.filter(p -> p.toString().endsWith(".java")).forEach(p -> {
                    try {
                        if (Files.readString(p).contains(paqueteProhibido)) {
                            infracciones.add(raiz.relativize(p).toString());
                        }
                    } catch (IOException ex) {
                        throw new UncheckedIOException(ex);
                    }
                });
            } catch (IOException ex) {
                throw new UncheckedIOException(ex);
            }
        }
        assertTrue(infracciones.isEmpty(), paqueteProhibido + " referenciado desde " + infracciones);
    }

    private static Path raiz() {
        Path cwd = Path.of(System.getProperty("user.dir")).toAbsolutePath().normalize();
        if (Files.exists(cwd.resolve(BASE))) {
            return cwd;
        }
        Path modulo = cwd.resolve("m1");
        if (Files.exists(modulo.resolve(BASE))) {
            return modulo;
        }
        throw new IllegalStateException("No se encontró el módulo m1 desde " + cwd);
    }
}
