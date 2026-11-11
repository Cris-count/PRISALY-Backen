package com.prisaly.m1.infrastructure.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.prisaly.m1.api.IdentityDomainException;
import com.prisaly.m1.application.service.InicializacionAdministradorService;
import com.prisaly.m1.application.service.InicializacionAdministradorService.Estado;
import com.prisaly.m1.application.service.InicializacionAdministradorService.Resultado;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;

/** P5-U1 (activation by property) and P5-U4 (the credential never reaches the logs), DC-P5-FASE-B-01. */
@ExtendWith(OutputCaptureExtension.class)
class InicializacionAdministradorRunnerTest {

    private static final String CLAVE = "Fixture#Runner-Secreta-2026";

    @TempDir Path dir;
    private final InicializacionAdministradorService servicio = mock(InicializacionAdministradorService.class);

    private InicializacionAdministradorRunner runner(String archivo) {
        return new InicializacionAdministradorRunner(servicio, "admin@prisaly.test", "Admin Inicial", archivo);
    }

    private Path archivo(String contenido) throws Exception {
        Path f = dir.resolve("credencial.txt");
        Files.writeString(f, contenido, StandardCharsets.UTF_8);
        return f;
    }

    @Test
    void sinPropiedadOFalseNoSeRegistraElRunner() {
        var contexto = new ApplicationContextRunner()
                .withBean(InicializacionAdministradorService.class, () -> servicio)
                .withUserConfiguration(InicializacionAdministradorRunner.class);
        contexto.run(c -> assertThat(c).doesNotHaveBean(InicializacionAdministradorRunner.class));
        contexto.withPropertyValues("prisaly.m1.inicializacion-admin.habilitada=false")
                .run(c -> assertThat(c).doesNotHaveBean(InicializacionAdministradorRunner.class));
        contexto.withPropertyValues("prisaly.m1.inicializacion-admin.habilitada=true")
                .run(c -> assertThat(c).hasSingleBean(InicializacionAdministradorRunner.class));
        verifyNoInteractions(servicio);
    }

    @Test
    void exitoPasaLaClaveSinElFinDeLineaYNoLaRegistra(CapturedOutput salida) throws Exception {
        UUID id = UUID.randomUUID();
        when(servicio.inicializar("Admin Inicial", "admin@prisaly.test", CLAVE, "ARRANQUE"))
                .thenReturn(new Resultado(Estado.INICIALIZADO, id));

        String linea = runner(archivo(CLAVE + "\r\n").toString()).ejecutar();

        verify(servicio).inicializar("Admin Inicial", "admin@prisaly.test", CLAVE, "ARRANQUE");
        assertTrue(linea.contains("resultado=EXITOSO") && linea.contains("usuario=" + id));
        assertFalse(salida.getAll().contains(CLAVE));
        assertFalse(salida.getAll().contains("admin@prisaly.test"));
        assertTrue(salida.getAll().contains("SEGURIDAD_ADMINISTRACION evento=INICIALIZACION resultado=EXITOSO"));
    }

    @Test
    void segundaEjecucionSeRegistraSinCambios(CapturedOutput salida) throws Exception {
        when(servicio.inicializar(any(), any(), any(), any())).thenReturn(new Resultado(Estado.YA_REALIZADA, null));

        String linea = runner(archivo(CLAVE).toString()).ejecutar();

        assertTrue(linea.contains("resultado=SIN_CAMBIOS codigo=INICIALIZACION_YA_REALIZADA"));
        assertFalse(salida.getAll().contains(CLAVE));
    }

    @Test
    void rechazoDeDominioSoloRegistraElCodigo(CapturedOutput salida) throws Exception {
        // The exception message deliberately contains the secret: the runner must not print it.
        when(servicio.inicializar(any(), any(), any(), any()))
                .thenThrow(new IdentityDomainException("PASSWORD_COMMON", "rechazada: " + CLAVE));

        String linea = runner(archivo(CLAVE).toString()).ejecutar();

        assertTrue(linea.contains("resultado=RECHAZADO codigo=PASSWORD_COMMON"));
        assertFalse(salida.getAll().contains(CLAVE));
    }

    @Test
    void errorTecnicoSoloRegistraElTipoYNoDetieneLaAplicacion(CapturedOutput salida) throws Exception {
        when(servicio.inicializar(any(), any(), any(), any()))
                .thenThrow(new IllegalStateException("INSERT ... values (" + CLAVE + ")"));

        String linea = runner(archivo(CLAVE).toString()).ejecutar();

        assertTrue(linea.contains("codigo=IllegalStateException"));
        assertFalse(salida.getAll().contains(CLAVE));
    }

    @Test
    void archivoAusenteVacioOMultilineaNoLlamaAlServicio(CapturedOutput salida) throws Exception {
        assertTrue(runner(dir.resolve("no-existe.txt").toString()).ejecutar().contains("CREDENTIAL_FILE_UNREADABLE"));
        assertTrue(runner("").ejecutar().contains("CREDENTIAL_FILE_UNREADABLE"));
        assertTrue(runner(archivo("\n").toString()).ejecutar().contains("CREDENTIAL_FILE_UNREADABLE"));
        assertTrue(runner(archivo(CLAVE + "\nsegunda-linea").toString()).ejecutar().contains("CREDENTIAL_FILE_UNREADABLE"));
        verifyNoInteractions(servicio);
        assertFalse(salida.getAll().contains(CLAVE));
    }

    @Test
    void espaciosInternosYFinalesFormanParteDeLaClave() throws Exception {
        when(servicio.inicializar(any(), any(), eq(" con espacios al borde "), any()))
                .thenReturn(new Resultado(Estado.INICIALIZADO, UUID.randomUUID()));

        assertTrue(runner(archivo(" con espacios al borde \n").toString()).ejecutar().contains("EXITOSO"));
    }
}
