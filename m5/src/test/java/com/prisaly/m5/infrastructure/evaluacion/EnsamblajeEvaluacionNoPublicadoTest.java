package com.prisaly.m5.infrastructure.evaluacion;

import com.prisaly.m6.api.SolicitudEvaluacion;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;

import com.prisaly.m4.api.ProgramacionParticipacionPort;
import com.prisaly.m5.application.acceso.AccesoProtegidoParticipacion;
import com.prisaly.m5.application.acceso.IdentidadEstudianteActual;
import com.prisaly.m5.application.port.RepositorioIntentos;
import com.prisaly.m5.infrastructure.identidad.IdentidadEstudianteDesdeM1;
import com.prisaly.m6.application.port.AutorizacionEvaluacionPort;
import com.prisaly.m6.application.port.ConsultaCalificacionPort;
import com.prisaly.m6.application.port.ConsultaContextoAcademicoPort;
import com.prisaly.m6.application.port.ConsultaIntentoEvaluacionPort;
import com.prisaly.m6.application.port.EvaluacionCasoUso;
import com.prisaly.m6.application.port.SolicitudCapacidadContextoAcademicoPort;
import com.prisaly.m6.application.proxy.EvaluacionProtectionProxy;
import com.prisaly.m6.application.service.PrepararContextoEvaluacionService;
import com.prisaly.m6.application.service.PrepararEvaluacionProtegida;
import com.prisaly.m6.domain.strategy.EstrategiaEvaluacion;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.function.Supplier;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;
import org.springframework.transaction.PlatformTransactionManager;

/**
 * APP component-scans {@code com.prisaly}. This test applies the same default stereotype scan to
 * every M5 and M6 production class and proves that nothing evaluative is published by default:
 * no global attempt reader, no factory, no institutional authorizer, no capability issuer, no
 * academic context provider, no Strategy, no Proxy, no preparation service, and no simulated
 * student identity. The opt-in {@link EnsamblajeConsultaIntentoEvaluacion} stays unregistered.
 */
class EnsamblajeEvaluacionNoPublicadoTest {
    private static final List<Class<?>> PROHIBIDOS = List.of(
            ConsultaIntentoEvaluacionPort.class, FabricaConsultaIntentoEvaluacion.class,
            EnsamblajeConsultaIntentoEvaluacion.class, AutorizacionEvaluacionPort.class,
            SolicitudCapacidadContextoAcademicoPort.class, ConsultaContextoAcademicoPort.class,
            PrepararEvaluacionProtegida.class, PrepararContextoEvaluacionService.class,
            EvaluacionProtectionProxy.class, EvaluacionCasoUso.class, EstrategiaEvaluacion.class);

    @Test
    void elEscaneoDeAppNoPublicaComponentesNiBeansEvaluativos() throws Exception {
        var candidatos = candidatos();
        assertFalse(candidatos.isEmpty(), "the scan must see the real M5/M6 components");
        for (Class<?> tipo : candidatos) {
            for (Class<?> prohibido : PROHIBIDOS) {
                assertFalse(prohibido.isAssignableFrom(tipo), tipo + " publishes " + prohibido.getSimpleName());
            }
            for (Method metodo : beanMethods(tipo)) {
                for (Class<?> prohibido : PROHIBIDOS) {
                    assertFalse(prohibido.isAssignableFrom(metodo.getReturnType())
                                    || metodo.getReturnType().isAssignableFrom(prohibido),
                            metodo + " may publish " + prohibido.getSimpleName());
                }
            }
        }
    }

    @Test
    void laUnicaIdentidadEstudiantilPublicadaEsLaDeM1() throws Exception {
        var identidades = new ArrayList<Class<?>>();
        for (Class<?> tipo : candidatos()) {
            if (IdentidadEstudianteActual.class.isAssignableFrom(tipo)) { identidades.add(tipo); }
            for (Method metodo : beanMethods(tipo)) {
                assertFalse(IdentidadEstudianteActual.class.isAssignableFrom(metodo.getReturnType()),
                        metodo + " would publish a student identity outside M1");
            }
        }
        assertEquals(List.of(IdentidadEstudianteDesdeM1.class), identidades);
    }

    @Test
    void ningunaAutoConfiguracionImportaElEnsamblajeOpcional() throws IOException {
        var recursos = Collections.list(getClass().getClassLoader().getResources(
                "META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports"));
        for (var recurso : recursos) {
            try (InputStream in = recurso.openStream()) {
                String contenido = new String(in.readAllBytes(), StandardCharsets.UTF_8);
                assertFalse(contenido.contains("com.prisaly.m5.infrastructure.evaluacion"), recurso.toString());
                assertFalse(contenido.contains("com.prisaly.m6"), recurso.toString());
            }
        }
    }

    @Test
    void laFabricaExigeCadaProveedorObligatorio() {
        var autorizacion = mock(AutorizacionEvaluacionPort.class);
        var protegida = mock(AccesoProtegidoParticipacion.class);
        var repositorio = mock(RepositorioIntentos.class);
        var identidad = mock(IdentidadEstudianteActual.class);
        var programacion = mock(ProgramacionParticipacionPort.class);
        var tx = mock(PlatformTransactionManager.class);
        List<Supplier<FabricaConsultaIntentoEvaluacion>> incompletas = List.of(
                () -> new FabricaConsultaIntentoEvaluacion(null, protegida, repositorio, identidad, programacion, tx),
                () -> new FabricaConsultaIntentoEvaluacion(autorizacion, null, repositorio, identidad, programacion, tx),
                () -> new FabricaConsultaIntentoEvaluacion(autorizacion, protegida, null, identidad, programacion, tx),
                () -> new FabricaConsultaIntentoEvaluacion(autorizacion, protegida, repositorio, null, programacion, tx),
                () -> new FabricaConsultaIntentoEvaluacion(autorizacion, protegida, repositorio, identidad, null, tx),
                () -> new FabricaConsultaIntentoEvaluacion(autorizacion, protegida, repositorio, identidad, programacion, null));
        for (var incompleta : incompletas) { assertThrows(NullPointerException.class, incompleta::get); }
        var fabrica = new FabricaConsultaIntentoEvaluacion(autorizacion, protegida, repositorio, identidad, programacion, tx);
        // Every request gets its own bound reader; there is no shared global instance to capture.
        assertThrows(NullPointerException.class, () -> fabrica.para(null));
        var solicitud = new com.prisaly.m6.api.SolicitudEvaluacion(java.util.UUID.randomUUID(),
                java.util.UUID.randomUUID());
        assertNotSame(fabrica.para(solicitud), fabrica.para(solicitud));
        assertEquals(0, Arrays.stream(FabricaConsultaIntentoEvaluacion.class.getMethods())
                .filter(m -> ConsultaIntentoEvaluacionPort.class.isAssignableFrom(m.getReturnType()))
                .filter(m -> m.getParameterCount() == 0).count(), "no parameterless way to obtain a reader");
        // Grades are only read by M6 preparation; nothing in this assembly can write them.
        assertEquals(0, Arrays.stream(ConsultaCalificacionPort.class.getMethods())
                .filter(m -> !m.getName().startsWith("find")).count());
    }

    private static List<Class<?>> candidatos() throws ClassNotFoundException {
        var scanner = new ClassPathScanningCandidateComponentProvider(true);
        var tipos = new ArrayList<Class<?>>();
        for (String paquete : List.of("com.prisaly.m5", "com.prisaly.m6")) {
            for (BeanDefinition definicion : scanner.findCandidateComponents(paquete)) {
                Class<?> tipo = Class.forName(definicion.getBeanClassName());
                // Production classes only: test fixtures (test-classes) are not packaged in APP.
                String origen = String.valueOf(tipo.getProtectionDomain().getCodeSource().getLocation());
                if (!origen.contains("test-classes")) { tipos.add(tipo); }
            }
        }
        return tipos;
    }

    private static List<Method> beanMethods(Class<?> tipo) {
        return Arrays.stream(tipo.getDeclaredMethods()).filter(m -> m.isAnnotationPresent(Bean.class)).toList();
    }
}
