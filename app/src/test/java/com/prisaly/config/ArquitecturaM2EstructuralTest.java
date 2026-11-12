package com.prisaly.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

/**
 * DC-CIERRE-MODULAR-01: structural conformity of M2 while PD-18 (its business responsibility) stays undecided.
 * M2 must remain a declared Maven module without business code, tables, routes or inbound dependencies, so that it
 * cannot silently become a mandatory intermediary between M1 and M7.
 */
class ArquitecturaM2EstructuralTest {
    private static final Path M2 = Path.of("..", "m2");
    private static final List<String> OTROS = List.of("m1", "m3", "m4", "m5", "m6", "m7");

    private static List<Path> archivos(Path raiz) throws IOException {
        if (!Files.exists(raiz)) { return List.of(); }
        try (Stream<Path> s = Files.walk(raiz)) { return s.filter(Files::isRegularFile).toList(); }
    }

    @Test
    void m2EsUnModuloMavenDeclaradoYEnsambladoPorApp() throws IOException {
        assertTrue(Files.readString(Path.of("..", "pom.xml")).contains("<module>m2</module>"));
        assertTrue(Files.readString(Path.of("pom.xml")).contains("<artifactId>m2</artifactId>"));
    }

    @Test
    void m2NoContieneCodigoDeNegocioNiRecursos() throws IOException {
        List<String> codigo = archivos(M2.resolve("src/main/java")).stream().map(p -> p.getFileName().toString()).toList();
        assertEquals(List.of("package-info.java"), codigo, "M2 has no business classes while PD-18 is open");
        assertEquals(List.of(), archivos(M2.resolve("src/main/resources")), "M2 has no tables, migrations or configuration");
    }

    @Test
    void m2NoDeclaraDependenciasNiEsDependenciaDeOtroModulo() throws IOException {
        assertFalse(Files.readString(M2.resolve("pom.xml")).contains("<dependencies>"), "M2 depends on no module");
        List<String> violaciones = new ArrayList<>();
        for (String modulo : OTROS) {
            if (Files.readString(Path.of("..", modulo, "pom.xml")).contains("<artifactId>m2</artifactId>")) {
                violaciones.add(modulo + "/pom.xml");
            }
            for (Path f : archivos(Path.of("..", modulo, "src", "main", "java"))) {
                if (f.toString().endsWith(".java") && Files.readString(f).contains("com.prisaly.m2")) {
                    violaciones.add(f.toString());
                }
            }
        }
        assertEquals(List.of(), violaciones, "no module may route through M2 without a ratified responsibility");
    }
}
