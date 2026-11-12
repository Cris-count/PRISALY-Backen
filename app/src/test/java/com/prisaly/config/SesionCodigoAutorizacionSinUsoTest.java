package com.prisaly.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

/**
 * DC-DD02-B1-01 (independent of pending decisions): BD-005.2 keeps {@code "Sesion"."codigoAutorizacionHash"} (V001),
 * but the session-wide code (model C) was discarded by Dirección Central as an authentication mechanism.
 * This guard fixes today's behaviour: the column is only mapped passively by M4 persistence; no service, controller,
 * security filter or other module reads or writes it, and no HTTP request/response or email template carries it.
 * It does not change any contract: it fails if someone starts using the column without a new decision.
 */
class SesionCodigoAutorizacionSinUsoTest {
    private static final List<String> MODULOS = List.of("m1", "m2", "m3", "m4", "m5", "m6", "m7", "app");
    /** Passive persistence mapping only (entity column, domain field and entity<->domain copy). */
    private static final Set<String> PERMITIDOS = Set.of(
            "m4/src/main/java/com/prisaly/m4/domain/model/SesionAcademica.java",
            "m4/src/main/java/com/prisaly/m4/infrastructure/persistence/SesionAcademicaEntity.java",
            "m4/src/main/java/com/prisaly/m4/infrastructure/persistence/EntityMapper.java");

    @Test
    void soloElMapeoPasivoDeM4ReferenciaLaColumna() throws IOException {
        List<String> referencias = new ArrayList<>();
        for (String modulo : MODULOS) {
            Path raiz = Path.of("..", modulo, "src", "main");
            if (!Files.isDirectory(raiz)) continue;
            try (Stream<Path> archivos = Files.walk(raiz)) {
                for (Path archivo : archivos.filter(Files::isRegularFile).toList()) {
                    String nombre = archivo.getFileName().toString();
                    // Historical migrations define the column; they are BD-005.2 and must stay untouched.
                    if (nombre.matches("V0\\d\\d__.+\\.sql")) continue;
                    if (Files.readString(archivo).toLowerCase().contains("codigoautorizacion")) {
                        referencias.add(Path.of("..").relativize(archivo).toString().replace('\\', '/'));
                    }
                }
            }
        }
        assertEquals(PERMITIDOS, Set.copyOf(referencias),
                "Sesion.codigoAutorizacionHash only has the passive M4 mapping; model C is not an authentication mechanism");
    }

    @Test
    void ningunaEntradaOSalidaDeM4NiPlantillaDeM7ExponeElCodigoDeSesion() throws IOException {
        // Request/response DTOs and controllers of M4 and the M7 convocation template must not carry the code.
        for (String dir : List.of("../m4/src/main/java/com/prisaly/m4/presentation", "../m7/src/main/java/com/prisaly/m7/application")) {
            Path raiz = Path.of(dir);
            assertTrue(Files.isDirectory(raiz), dir);
            try (Stream<Path> archivos = Files.walk(raiz)) {
                for (Path archivo : archivos.filter(p -> p.toString().endsWith(".java")).toList()) {
                    String texto = Files.readString(archivo).toLowerCase();
                    assertTrue(!texto.contains("codigoautorizacion") && !texto.contains("{codigo}"), archivo.toString());
                }
            }
        }
    }
}
