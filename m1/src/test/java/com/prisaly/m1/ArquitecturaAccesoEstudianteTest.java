package com.prisaly.m1;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

class ArquitecturaAccesoEstudianteTest {

    @Test
    void m1NoDependeDeOtrosModulosNiImportaSuAcceso() throws Exception {
        Path raiz = raiz();
        String pom = Files.readString(raiz.resolve("pom.xml"));
        for (String modulo : new String[] {"m2", "m3", "m4", "m5", "m6", "m7"}) {
            assertFalse(pom.contains("<artifactId>" + modulo + "</artifactId>"), modulo);
        }
        try (Stream<Path> fuentes = Files.walk(raiz.resolve("src/main/java"))) {
            fuentes.filter(path -> path.toString().endsWith(".java")).forEach(path -> {
                try {
                    String texto = Files.readString(path);
                    for (String paquete : new String[] {
                            "com.prisaly.m2", "com.prisaly.m3", "com.prisaly.m4",
                            "com.prisaly.m5", "com.prisaly.m6", "com.prisaly.m7"}) {
                        assertFalse(texto.contains("import " + paquete), path + " " + paquete);
                    }
                    assertFalse(texto.contains("ControlAccesoIntentos"));
                    assertFalse(texto.contains("ParticipacionFacade"));
                } catch (Exception ex) {
                    throw new IllegalStateException(path.toString(), ex);
                }
            });
        }
        String controlador = Files.readString(raiz.resolve(
                "src/main/java/com/prisaly/m1/presentation/EstudianteCuentaController.java"));
        assertTrue(controlador.contains("challengeId"));
        assertFalse(controlador.contains("studentId"));
    }

    private static Path raiz() {
        Path cwd = Path.of(System.getProperty("user.dir")).toAbsolutePath().normalize();
        if (Files.exists(cwd.resolve("src/main/java/com/prisaly/m1"))) {
            return cwd;
        }
        Path modulo = cwd.resolve("m1");
        if (Files.exists(modulo.resolve("src/main/java/com/prisaly/m1"))) {
            return modulo;
        }
        throw new IllegalStateException("No se encontró el módulo m1 desde " + cwd);
    }
}
