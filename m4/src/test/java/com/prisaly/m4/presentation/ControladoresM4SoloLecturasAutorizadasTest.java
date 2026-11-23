package com.prisaly.m4.presentation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.prisaly.m4.application.service.SesionAcademicaService;
import java.io.IOException;
import java.lang.reflect.Modifier;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

/**
 * DC-BE-CIERRE-FUNCIONAL-NOCTURNO-67 (DC-62 §7, M4-LATENTE): a teacher-facing M4 controller may only read sessions
 * and calls through the authorized service methods (GESTIONAR_SESION + owner). {@code consultarSesion(UUID)} stays
 * public for internal use and tests, but no controller may call it, and no unauthorized call listing exists.
 */
class ControladoresM4SoloLecturasAutorizadasTest {
    private static final Pattern LLAMADA = Pattern.compile("service\\.(\\w+)\\(");
    private static final List<String> LECTURAS_AUTORIZADAS = List.of(
            "consultarSesionAutorizada", "listarSesionesAutorizadas", "consultarConvocatoriasAutorizadas");

    @Test
    void losControladoresSoloLeenMedianteMetodosAutorizados() throws IOException {
        Path raiz = Path.of("src", "main", "java", "com", "prisaly", "m4", "presentation", "controller");
        List<String> violaciones = new ArrayList<>();
        int llamadas = 0;
        try (Stream<Path> archivos = Files.list(raiz)) {
            for (Path archivo : archivos.filter(p -> p.toString().endsWith("Controller.java")).toList()) {
                var m = LLAMADA.matcher(Files.readString(archivo));
                while (m.find()) {
                    llamadas++;
                    String metodo = m.group(1);
                    if ((metodo.startsWith("consultar") || metodo.startsWith("listar"))
                            && !LECTURAS_AUTORIZADAS.contains(metodo)) {
                        violaciones.add(archivo.getFileName() + " -> " + metodo);
                    }
                }
            }
        }
        assertTrue(llamadas > 0, "controllers were analysed");
        assertEquals(List.of(), violaciones);
    }

    @Test
    void noExisteListadoDeConvocatoriasSinAutorizacion() {
        var publicos = Arrays.stream(SesionAcademicaService.class.getDeclaredMethods())
                .filter(m -> Modifier.isPublic(m.getModifiers())).map(java.lang.reflect.Method::getName).toList();
        assertFalse(publicos.contains("consultarConvocatorias"), publicos.toString());
        assertTrue(publicos.contains("consultarConvocatoriasAutorizadas"));
    }
}
