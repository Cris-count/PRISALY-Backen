package com.prisaly.m4.infrastructure;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.prisaly.m4.application.port.*;
import com.prisaly.m4.application.service.SesionAcademicaService;
import com.prisaly.m4.domain.model.SesionAcademica;
import com.prisaly.m4.domain.rule.ReglaNegocioException;
import com.prisaly.m4.infrastructure.adapter.SesionRepositoryAdapter;
import com.prisaly.m4.infrastructure.config.M4ProviderAutoConfiguration;
import com.prisaly.m4.infrastructure.persistence.JpaSesionRepository;
import com.prisaly.m4.presentation.controller.M4ExceptionHandler;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.http.HttpStatus;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

class M4ProviderSecurityTest {
    private final ApplicationContextRunner runner =
            new ApplicationContextRunner()
                    .withConfiguration(AutoConfigurations.of(M4ProviderAutoConfiguration.class));

    @ParameterizedTest
    @ValueSource(strings = {"default", "integration", "production", "test", "m4-test-doubles"})
    void perfilesNoHabilitanProveedoresPermisivos(String perfil) {
        runner.withPropertyValues("spring.profiles.active=" + perfil)
                .run(
                        context -> {
                            assertNull(context.getStartupFailure());
                            UUID id = UUID.randomUUID();
                            assertFalse(
                                    context.getBean(IdentidadPort.class)
                                            .tienePermiso(id, "GESTIONAR_SESION"));
                            assertNoDisponible(
                                    () ->
                                            context.getBean(CasoAcademicoPort.class)
                                                    .estaPublicado(id));
                            assertNoDisponible(
                                    () ->
                                            context.getBean(CasoAcademicoPort.class)
                                                    .solicitarBloqueoAcademico(id));
                            assertNoDisponible(
                                    () ->
                                            context.getBean(ParticipacionesPort.class)
                                                    .tieneParticipacionesOIntentos(id));
                            assertNoDisponible(
                                    () ->
                                            context.getBean(NotificacionPort.class)
                                                    .notificarConvocatoria(id, id));
                        });
    }

    @Test
    void proveedoresExplicitosReemplazanSoloSusFallbacks() {
        IdentidadPort identidad = mock(IdentidadPort.class);
        CasoAcademicoPort casos = mock(CasoAcademicoPort.class);
        ParticipacionesPort participaciones = mock(ParticipacionesPort.class);
        NotificacionPort notificaciones = mock(NotificacionPort.class);
        runner.withBean(IdentidadPort.class, () -> identidad)
                .withBean(CasoAcademicoPort.class, () -> casos)
                .withBean(ParticipacionesPort.class, () -> participaciones)
                .withBean(NotificacionPort.class, () -> notificaciones)
                .run(
                        context -> {
                            assertNull(context.getStartupFailure());
                            assertSame(identidad, context.getBean(IdentidadPort.class));
                            assertSame(casos, context.getBean(CasoAcademicoPort.class));
                            assertSame(participaciones, context.getBean(ParticipacionesPort.class));
                            assertSame(notificaciones, context.getBean(NotificacionPort.class));
                        });
    }

    @Test
    void sinM1NoSeConsultaNiPersiste() {
        runner.run(
                context -> {
                    var repositorio = mock(SesionRepository.class);
                    var casos = mock(CasoAcademicoPort.class);
                    var service =
                            new SesionAcademicaService(
                                    repositorio,
                                    mock(ConvocatoriaRepository.class),
                                    context.getBean(IdentidadPort.class),
                                    casos,
                                    mock(NotificacionPort.class));
                    var inicio = Instant.now().plus(2, java.time.temporal.ChronoUnit.DAYS);
                    var error =
                            assertThrows(
                                    ReglaNegocioException.class,
                                    () ->
                                            service.crearSesion(
                                                    UUID.randomUUID(),
                                                    UUID.randomUUID(),
                                                    "Protegida",
                                                    null,
                                                    inicio,
                                                    inicio.plus(
                                                            1, java.time.temporal.ChronoUnit.HOURS),
                                                    1800));
                    assertEquals("M4-SIN-PERMISO", error.getCodigo());
                    verifyNoInteractions(repositorio, casos);
                });
    }

    @Test
    void sinM3NoSePersisteAunqueM1Autorice() {
        runner.run(
                context -> {
                    var repositorio = mock(SesionRepository.class);
                    var service =
                            new SesionAcademicaService(
                                    repositorio,
                                    mock(ConvocatoriaRepository.class),
                                    (id, permiso) -> true,
                                    context.getBean(CasoAcademicoPort.class),
                                    mock(NotificacionPort.class));
                    var inicio = Instant.now().plus(2, java.time.temporal.ChronoUnit.DAYS);
                    assertNoDisponible(
                            () ->
                                    service.crearSesion(
                                            UUID.randomUUID(),
                                            UUID.randomUUID(),
                                            "Protegida",
                                            null,
                                            inicio,
                                            inicio.plus(1, java.time.temporal.ChronoUnit.HOURS),
                                            1800));
                    verify(repositorio, never()).guardar(any());
                });
    }

    @Test
    void sinM5SeBloqueanActualizacionYReasignacion() {
        runner.run(
                context -> {
                    var jpa = mock(JpaSesionRepository.class);
                    var adapter =
                            new SesionRepositoryAdapter(
                                    jpa, context.getBean(ParticipacionesPort.class));
                    var repositorio = mock(SesionRepository.class);
                    UUID docente = UUID.randomUUID();
                    var inicio = Instant.now().plus(2, java.time.temporal.ChronoUnit.DAYS);
                    var sesion =
                            SesionAcademica.crear(
                                    UUID.randomUUID(),
                                    docente,
                                    "Original",
                                    null,
                                    inicio,
                                    inicio.plus(1, java.time.temporal.ChronoUnit.HOURS),
                                    1800);
                    when(repositorio.buscarPorId(sesion.getId())).thenReturn(Optional.of(sesion));
                    when(repositorio.buscarParaActualizar(sesion.getId()))
                            .thenReturn(Optional.of(sesion));
                    when(repositorio.tieneParticipacionesVinculadas(sesion.getId()))
                            .thenAnswer(
                                    invocation ->
                                            adapter.tieneParticipacionesVinculadas(sesion.getId()));
                    var service =
                            new SesionAcademicaService(
                                    repositorio,
                                    mock(ConvocatoriaRepository.class),
                                    (id, permiso) -> true,
                                    mock(CasoAcademicoPort.class),
                                    mock(NotificacionPort.class));
                    assertNoDisponible(
                            () ->
                                    service.actualizarSesion(
                                            docente,
                                            sesion.getId(),
                                            "Cambio",
                                            null,
                                            inicio.plus(1, java.time.temporal.ChronoUnit.HOURS),
                                            inicio.plus(2, java.time.temporal.ChronoUnit.HOURS)));
                    assertNoDisponible(
                            () -> service.cambiarCaso(docente, sesion.getId(), UUID.randomUUID()));
                    assertEquals("Original", sesion.getTitulo());
                    verify(repositorio, never()).guardar(any());
                    verifyNoInteractions(jpa);
                });
    }

    @Test
    void proveedorAusenteSeExponeComo503() {
        var error = new ReglaNegocioException("M4-PROVEEDOR-NO-DISPONIBLE", "Proveedor ausente");
        assertEquals(
                HttpStatus.SERVICE_UNAVAILABLE,
                new M4ExceptionHandler().handleReglaNegocio(error).getStatusCode());
    }

    private static void assertNoDisponible(org.junit.jupiter.api.function.Executable operation) {
        var error = assertThrows(ReglaNegocioException.class, operation);
        assertEquals("M4-PROVEEDOR-NO-DISPONIBLE", error.getCodigo());
    }
}
