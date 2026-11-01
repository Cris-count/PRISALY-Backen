package com.prisaly.m4.application;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.prisaly.m4.application.port.*;
import com.prisaly.m4.application.service.SesionAcademicaService;
import com.prisaly.m4.domain.model.SesionAcademica;
import com.prisaly.m4.domain.rule.ReglaNegocioException;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

class AutorizacionM4ServiceTest {
    private final SesionRepository sesiones = mock(SesionRepository.class);
    private final ConvocatoriaRepository convocatorias = mock(ConvocatoriaRepository.class);
    private final IdentidadPort identidad = mock(IdentidadPort.class);
    private final CasoAcademicoPort casos = mock(CasoAcademicoPort.class);
    private final UUID actual = UUID.randomUUID();
    private SesionAcademicaService service;

    @BeforeEach
    void preparar() {
        service =
                new SesionAcademicaService(
                        sesiones, convocatorias, identidad, casos, mock(NotificacionPort.class));
        when(identidad.docenteActual()).thenReturn(actual);
    }

    @Test
    void proveedorSinIdentidadNoProvocaErrorInterno() {
        when(identidad.docenteActual()).thenReturn(null);
        var error =
                assertThrows(
                        ReglaNegocioException.class,
                        () -> service.resolverDocenteAutenticado(null));
        assertEquals("M4-NO-AUTENTICADO", error.getCodigo());
        verifyNoInteractions(sesiones, convocatorias, casos);
    }

    @Test
    void mutacionSinDocenteNoConsultaNiBloqueaLaSesion() {
        var error =
                assertThrows(
                        ReglaNegocioException.class,
                        () ->
                                service.cambiarEstado(
                                        null,
                                        UUID.randomUUID(),
                                        com.prisaly.m4.domain.model.EstadoSesion.EN_CURSO));
        assertEquals("M4-NO-AUTENTICADO", error.getCodigo());
        verifyNoInteractions(sesiones, convocatorias, casos);
    }

    @Test
    void identidadDeclaradaNoPuedeSuplantarAlPrincipal() {
        assertEquals(actual, service.resolverDocenteAutenticado(null));
        var error =
                assertThrows(
                        ReglaNegocioException.class,
                        () -> service.resolverDocenteAutenticado(UUID.randomUUID()));
        assertEquals("M4-SIN-PERMISO", error.getCodigo());
        verifyNoInteractions(sesiones, convocatorias, casos);
    }

    @Test
    void listadoSoloDevuelveSesionesDelDocenteActual() {
        when(identidad.tienePermiso(actual, "GESTIONAR_SESION")).thenReturn(true);
        when(sesiones.listarPorDocente(actual)).thenReturn(List.of());
        assertTrue(service.listarSesionesAutorizadas(null).isEmpty());
        verify(sesiones).listarPorDocente(actual);
        verify(sesiones, never()).listarTodas();
        assertThrows(
                ReglaNegocioException.class,
                () -> service.listarSesionesAutorizadas(UUID.randomUUID()));
    }

    @Test
    void lecturaDeSesionAjenaNoDevuelveDatosNiConvocatorias() {
        when(identidad.tienePermiso(actual, "GESTIONAR_SESION")).thenReturn(true);
        var sesion = sesion(UUID.randomUUID());
        when(sesiones.buscarPorId(sesion.getId())).thenReturn(Optional.of(sesion));
        when(sesiones.buscarParaActualizar(sesion.getId())).thenReturn(Optional.of(sesion));
        var error =
                assertThrows(
                        ReglaNegocioException.class,
                        () -> service.consultarConvocatoriasAutorizadas(sesion.getId()));
        assertEquals("M4-SESION-AJENA", error.getCodigo());
        verifyNoInteractions(convocatorias);
    }

    @Test
    void sinPermisoNoConsultaLaSesion() {
        assertThrows(
                ReglaNegocioException.class,
                () -> service.consultarSesionAutorizada(UUID.randomUUID()));
        verifyNoInteractions(sesiones);
    }

    @Test
    void reasignacionSolicitaBloqueoDelPrimerCasoDestino() {
        var sesion = sesion(actual);
        UUID destino = UUID.randomUUID();
        when(identidad.tienePermiso(actual, "GESTIONAR_SESION")).thenReturn(true);
        when(sesiones.buscarPorId(sesion.getId())).thenReturn(Optional.of(sesion));
        when(sesiones.buscarParaActualizar(sesion.getId())).thenReturn(Optional.of(sesion));
        when(casos.estaPublicado(destino)).thenReturn(true);
        when(sesiones.guardar(any())).thenAnswer(invocation -> invocation.getArgument(0));
        var resultado = service.cambiarCaso(actual, sesion.getId(), destino);
        assertEquals(destino, resultado.getCasoId());
        var orden = inOrder(sesiones, casos);
        orden.verify(sesiones).guardar(any());
        orden.verify(casos).solicitarBloqueoAcademico(destino);
    }

    @Test
    void convocatoriaSinParticipacionTambienProtegeElCasoSegunV005() {
        var sesion = sesion(actual);
        when(identidad.tienePermiso(actual, "GESTIONAR_SESION")).thenReturn(true);
        when(sesiones.buscarPorId(sesion.getId())).thenReturn(Optional.of(sesion));
        when(sesiones.buscarParaActualizar(sesion.getId())).thenReturn(Optional.of(sesion));
        when(convocatorias.listarPorSesion(sesion.getId()))
                .thenReturn(
                        List.of(
                                com.prisaly.m4.domain.model.Convocatoria.crear(
                                        sesion.getId(), UUID.randomUUID())));
        var error =
                assertThrows(
                        ReglaNegocioException.class,
                        () -> service.cambiarCaso(actual, sesion.getId(), UUID.randomUUID()));
        assertEquals("M4-SESION-CON-CONVOCATORIAS", error.getCodigo());
        verify(sesiones, never()).guardar(any());
        verifyNoInteractions(casos);
    }

    private SesionAcademica sesion(UUID docente) {
        var inicio = Instant.now().plus(2, java.time.temporal.ChronoUnit.DAYS);
        return SesionAcademica.crear(
                UUID.randomUUID(),
                docente,
                "Sesión",
                null,
                inicio,
                inicio.plus(1, java.time.temporal.ChronoUnit.HOURS),
                1800);
    }
}
