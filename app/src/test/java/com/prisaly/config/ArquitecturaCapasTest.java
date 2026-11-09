package com.prisaly.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

/** Guarda estática de las cuatro capas y de los contratos permitidos entre módulos. */
class ArquitecturaCapasTest {
    private static final Pattern IMPORT = Pattern.compile("^import (?:static )?([\\w.]+)", Pattern.MULTILINE);
    private static final Pattern PAQUETE = Pattern.compile("^package ([\\w.]+);", Pattern.MULTILINE);
    private static final Pattern MODULO = Pattern.compile("^com\\.prisaly\\.(m[1-7])\\.");
    private static final List<String> MODULOS = List.of("m1", "m2", "m3", "m4", "m5", "m6", "m7");

    /** Contratos de M6 que M5 implementa, y el adaptador alterno de M4 descrito en el registro A6. */
    private static final Set<String> EXCEPCIONES_DOCUMENTADAS = Set.of(
            "com.prisaly.m6.application.AccesoEvaluacionDenegadoException",
            "com.prisaly.m3.domain.model.Caso");

    private record Fuente(Path archivo, String paquete, List<String> imports) { }

    private static List<Fuente> fuentes() throws IOException {
        List<Fuente> todas = new ArrayList<>();
        for (String modulo : MODULOS) {
            Path raiz = Path.of("..", modulo, "src", "main", "java");
            try (Stream<Path> archivos = Files.walk(raiz)) {
                for (Path archivo : archivos.filter(p -> p.toString().endsWith(".java")).toList()) {
                    String texto = Files.readString(archivo);
                    Matcher paquete = PAQUETE.matcher(texto);
                    if (!paquete.find()) {
                        continue;
                    }
                    List<String> imports = new ArrayList<>();
                    for (Matcher m = IMPORT.matcher(texto); m.find();) {
                        imports.add(m.group(1));
                    }
                    todas.add(new Fuente(archivo, paquete.group(1), imports));
                }
            }
        }
        return todas;
    }

    private static boolean enCapa(Fuente fuente, String capa) {
        return fuente.paquete().contains("." + capa);
    }

    private static void sinViolaciones(List<String> violaciones) {
        assertEquals(List.of(), violaciones);
    }

    @Test
    void seAnalizaElCodigoDeLosModulos() throws IOException {
        assertTrue(fuentes().size() > 300);
    }

    @Test
    void aplicacionDominioYPresentacionNoDependenDeInfraestructura() throws IOException {
        List<String> violaciones = new ArrayList<>();
        for (Fuente f : fuentes()) {
            if (enCapa(f, "application") || enCapa(f, "domain") || enCapa(f, "presentation")) {
                f.imports().stream().filter(i -> i.contains(".infrastructure."))
                        .forEach(i -> violaciones.add(f.archivo() + " -> " + i));
            }
        }
        sinViolaciones(violaciones);
    }

    @Test
    void dominioNoDependeDeFrameworkNiAplicacion() throws IOException {
        List<String> violaciones = new ArrayList<>();
        for (Fuente f : fuentes()) {
            if (enCapa(f, "domain")) {
                f.imports().stream()
                        .filter(i -> i.startsWith("org.springframework") || i.startsWith("jakarta.persistence")
                                || i.contains(".application."))
                        .forEach(i -> violaciones.add(f.archivo() + " -> " + i));
            }
        }
        sinViolaciones(violaciones);
    }

    @Test
    void presentacionNoRecibeRepositoriosNiAccedeAPersistencia() throws IOException {
        List<String> violaciones = new ArrayList<>();
        for (Fuente f : fuentes()) {
            if (enCapa(f, "presentation")) {
                f.imports().stream()
                        .filter(i -> i.endsWith("RepositoryPort") || i.contains("JdbcTemplate")
                                || (i.startsWith("jakarta.persistence") && !i.endsWith("Exception")))
                        .forEach(i -> violaciones.add(f.archivo() + " -> " + i));
            }
        }
        sinViolaciones(violaciones);
    }

    @Test
    void losModulosSoloUsanContratosPublicadosDeOtrosModulos() throws IOException {
        List<String> violaciones = new ArrayList<>();
        for (Fuente f : fuentes()) {
            Matcher propio = MODULO.matcher(f.paquete() + ".");
            if (!propio.find()) {
                continue;
            }
            for (String i : f.imports()) {
                Matcher destino = MODULO.matcher(i);
                if (destino.find() && !destino.group(1).equals(propio.group(1)) && !permitido(i)) {
                    violaciones.add(f.archivo() + " -> " + i);
                }
            }
        }
        sinViolaciones(violaciones);
    }

    private static boolean permitido(String importado) {
        return importado.contains(".api.") || importado.contains(".application.port.")
                || importado.contains(".application.integration.") || EXCEPCIONES_DOCUMENTADAS.contains(importado);
    }
}
