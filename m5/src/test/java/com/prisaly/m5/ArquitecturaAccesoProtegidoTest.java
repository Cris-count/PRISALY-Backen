package com.prisaly.m5;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.lang.annotation.Annotation;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.Type;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.prisaly.m5.presentation.ManejadorErroresM5;

/**
 * Reglas estructurales que impiden eludir el proxy de protección de M5.
 *
 * <ul>
 *   <li>Presentación (incluidos futuros controladores) solo puede depender de
 *       {@code OperacionesParticipacion} o {@code AccesoProtegidoParticipacion};
 *       nunca de la fachada sin control, servicios, puertos, repositorios ni
 *       infraestructura.</li>
 *   <li>Todo controlador de M5 vive en {@code presentation}.</li>
 *   <li>La fachada, su decorador, el control de acceso y el proxy solo se
 *       construyen en {@code ConfiguracionParticipacion}.</li>
 * </ul>
 */
@DisplayName("Arquitectura: el acceso de estudiante pasa siempre por el proxy")
class ArquitecturaAccesoProtegidoTest {

    private static final String BASE = "com.prisaly.m5";

    /** Tipos o paquetes que un componente de presentación no puede recibir ni declarar. */
    private static final List<String> PROHIBIDOS_EN_PRESENTACION = List.of(
            BASE + ".application.ParticipacionFacade",
            BASE + ".application.servicio.",
            BASE + ".application.port.",
            BASE + ".application.acceso.ControlAccesoIntentos",
            BASE + ".application.acceso.IdentidadEstudianteActual",
            BASE + ".application.acceso.TitularidadIntentos",
            BASE + ".infrastructure.",
            BASE + ".domain.intento.Intento ",
            "com.prisaly.m4.",
            "com.prisaly.m1.",
            "jakarta.persistence.",
            "org.springframework.data.",
            "org.springframework.jdbc.");

    /** Construcciones permitidas solo en ConfiguracionParticipacion, con nombre simple o calificado. */
    private static final Pattern CONSTRUCCION_RESERVADA = Pattern.compile(
            "new\\s+(?:[\\w.]+\\.)?(ParticipacionFacade|FachadaParticipacionTransaccional"
                    + "|ControlAccesoIntentos|AccesoProtegidoParticipacion)\\s*\\(");

    @Test
    @DisplayName("presentación no recibe ni expone tipos que eludan el control de acceso")
    void presentacionSoloUsaElProxy() throws Exception {
        List<String> violaciones = new ArrayList<>();
        List<Class<?>> presentacion = clasesBajo(BASE + ".presentation");
        assertFalse(presentacion.isEmpty(), "no se encontraron clases de presentación");
        for (Class<?> clase : presentacion) {
            for (String firma : firmas(clase)) {
                for (String prohibido : PROHIBIDOS_EN_PRESENTACION) {
                    if ((firma + " ").contains(prohibido)) {
                        violaciones.add(clase.getName() + " -> " + firma);
                    }
                }
            }
        }
        assertEquals(List.of(), violaciones);
    }

    @Test
    @DisplayName("todo controlador de M5 está en presentación (donde se aplican estas reglas)")
    void controladoresSoloEnPresentacion() throws Exception {
        List<String> fuera = new ArrayList<>();
        for (Class<?> clase : clasesBajo(BASE)) {
            boolean controlador = Stream.of(clase.getAnnotations())
                    .map(Annotation::annotationType)
                    .map(Class::getSimpleName)
                    .anyMatch(nombre -> nombre.equals("RestController") || nombre.equals("Controller"));
            if (controlador && !clase.getPackageName().startsWith(BASE + ".presentation")) {
                fuera.add(clase.getName());
            }
        }
        assertEquals(List.of(), fuera);
    }

    @Test
    @DisplayName("la fachada sin control y el proxy solo se construyen en ConfiguracionParticipacion")
    void construccionReservada() throws IOException {
        Path fuentes = fuentesPrincipales();
        List<String> violaciones = new ArrayList<>();
        try (Stream<Path> archivos = Files.walk(fuentes)) {
            for (Path archivo : archivos.filter(p -> p.toString().endsWith(".java")).toList()) {
                if (archivo.getFileName().toString().equals("ConfiguracionParticipacion.java")) {
                    continue;
                }
                Matcher construccion = CONSTRUCCION_RESERVADA.matcher(Files.readString(archivo, StandardCharsets.UTF_8));
                while (construccion.find()) {
                    violaciones.add(fuentes.relativize(archivo) + ": " + construccion.group(1));
                }
            }
        }
        assertEquals(List.of(), violaciones);
    }

    @Test
    @DisplayName("los repositorios Spring Data de M5 no son públicos")
    void repositoriosSpringDataNoPublicos() throws Exception {
        for (String nombre : List.of("IntentoJpaRepository", "RespuestaJpaRepository")) {
            Class<?> repositorio = Class.forName(BASE + ".infrastructure.persistencia." + nombre);
            assertFalse(Modifier.isPublic(repositorio.getModifiers()), nombre);
        }
    }

    @Test
    @DisplayName("el proxy y el control de acceso no pueden extenderse para saltarse la verificación")
    void proxyFinal() throws Exception {
        assertTrue(Modifier.isFinal(Class.forName(BASE + ".application.acceso.AccesoProtegidoParticipacion").getModifiers()));
        assertTrue(Modifier.isFinal(Class.forName(BASE + ".application.acceso.ControlAccesoIntentos").getModifiers()));
    }

    private static List<String> firmas(Class<?> clase) {
        List<String> firmas = new ArrayList<>();
        for (Field campo : clase.getDeclaredFields()) {
            firmas.add(campo.getGenericType().getTypeName());
        }
        for (Constructor<?> constructor : clase.getDeclaredConstructors()) {
            for (Type parametro : constructor.getGenericParameterTypes()) {
                firmas.add(parametro.getTypeName());
            }
        }
        for (Method metodo : clase.getDeclaredMethods()) {
            firmas.add(metodo.getGenericReturnType().getTypeName());
            for (Type parametro : metodo.getGenericParameterTypes()) {
                firmas.add(parametro.getTypeName());
            }
        }
        return firmas;
    }

    private static List<Class<?>> clasesBajo(String paquete) throws IOException, URISyntaxException, ClassNotFoundException {
        Path raiz = Path.of(ManejadorErroresM5.class.getProtectionDomain().getCodeSource().getLocation().toURI());
        Path directorio = raiz.resolve(paquete.replace('.', '/'));
        List<Class<?>> clases = new ArrayList<>();
        try (Stream<Path> archivos = Files.walk(directorio)) {
            for (Path archivo : archivos.filter(p -> p.toString().endsWith(".class")).toList()) {
                String relativo = raiz.relativize(archivo).toString().replace('\\', '/');
                String nombre = relativo.substring(0, relativo.length() - ".class".length()).replace('/', '.');
                if (!nombre.endsWith("package-info")) {
                    clases.add(Class.forName(nombre, false, ArquitecturaAccesoProtegidoTest.class.getClassLoader()));
                }
            }
        }
        return clases;
    }

    private static Path fuentesPrincipales() {
        Path actual = Path.of(System.getProperty("user.dir")).toAbsolutePath().normalize();
        for (Path candidata : List.of(
                actual.resolve("src/main/java/com/prisaly/m5"),
                actual.resolve("m5/src/main/java/com/prisaly/m5"))) {
            if (Files.isDirectory(candidata)) {
                return candidata;
            }
        }
        throw new IllegalStateException("No se encontraron las fuentes de M5 desde user.dir=" + actual);
    }
}
