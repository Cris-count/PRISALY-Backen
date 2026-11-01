package com.prisaly.m4.application;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.prisaly.m4.application.port.*;
import com.prisaly.m4.application.service.SesionAcademicaService;
import com.prisaly.m4.domain.model.*;
import com.prisaly.m4.domain.rule.*;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.*;

class SesionAcademicaServiceTest {
    private SesionRepository repo;
    private ConvocatoriaRepository conv;
    private IdentidadPort identidad;
    private CasoAcademicoPort casos;
    private NotificacionPort notificaciones;
    private SesionAcademicaService service;
    private final UUID docente = UUID.randomUUID(), caso = UUID.randomUUID();
    private final Instant inicio = Instant.parse("2030-11-05T09:00:00Z"),
            fin = inicio.plusSeconds(3600);

    @BeforeEach
    void configurar() {
        repo = mock(SesionRepository.class);
        conv = mock(ConvocatoriaRepository.class);
        identidad = mock(IdentidadPort.class);
        casos = mock(CasoAcademicoPort.class);
        notificaciones = mock(NotificacionPort.class);
        service = new SesionAcademicaService(repo, conv, identidad, casos, notificaciones);
        when(identidad.tienePermiso(any(), any())).thenReturn(true);
        when(casos.estaPublicado(any())).thenReturn(true);
        when(repo.guardar(any())).thenAnswer(i -> i.getArgument(0));
        when(conv.guardar(any())).thenAnswer(i -> i.getArgument(0));
    }

    private SesionAcademica sesion() {
        var s = SesionAcademica.crear(caso, docente, "Espacio", null, inicio, fin, 1800);
        when(repo.buscarPorId(s.getId())).thenReturn(Optional.of(s));
        when(repo.buscarParaActualizar(s.getId())).thenReturn(Optional.of(s));
        return s;
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(ints = {0, -1})
    void duracionInvalidaSeRechazaAntesDeBloquearProveedores(int duracion) {
        var error =
                assertThrows(
                        ReglaNegocioException.class,
                        () ->
                                service.crearSesion(
                                        docente, caso, "Espacio", null, inicio, fin, duracion));
        assertEquals("M4-DURACION-INVALIDA", error.getCodigo());
        verifyNoInteractions(repo, casos);
    }

    @Test
    void rangoInvalidoSeRechazaAntesDeBloquearProveedores() {
        var error =
                assertThrows(
                        ReglaNegocioException.class,
                        () ->
                                service.crearSesion(
                                        docente, caso, "Espacio", null, fin, inicio, 1800));
        assertEquals("M4-FECHA-INVALIDA", error.getCodigo());
        verifyNoInteractions(repo, casos);
    }

    @Test
    void programaYConfirmaPrimeraMarca() {
        var s = service.crearSesion(docente, caso, "Espacio", null, inicio, fin, 1800);
        assertEquals(EstadoSesion.SIN_INICIAR, s.getEstado());
        var orden = inOrder(repo, casos);
        orden.verify(repo).protegerProgramacion(caso, docente);
        orden.verify(repo).guardar(any());
        orden.verify(casos).solicitarBloqueoAcademico(caso);
    }

    @Test
    void noConcedePermisoPorDefecto() {
        when(identidad.tienePermiso(any(), any())).thenReturn(false);
        assertThrows(
                ReglaNegocioException.class,
                () -> service.crearSesion(docente, caso, "Espacio", null, inicio, fin, 1800));
        verifyNoInteractions(repo, casos);
    }

    @Test
    void rechazaBorrador() {
        when(casos.estaPublicado(caso)).thenReturn(false);
        var e =
                assertThrows(
                        ReglaNegocioException.class,
                        () ->
                                service.crearSesion(
                                        docente, caso, "Espacio", null, inicio, fin, 1800));
        assertEquals("M4-CASO-NO-PUBLICADO", e.getCodigo());
        verify(repo, never()).guardar(any());
    }

    @Test
    void rechazaDuplicado() {
        when(repo.existeDuplicada(caso, docente, inicio, fin)).thenReturn(true);
        assertThrows(
                ReglaNegocioException.class,
                () -> service.crearSesion(docente, caso, "Espacio", null, inicio, fin, 1800));
        verify(repo, never()).guardar(any());
    }

    @Test
    void lecturaInexistente() {
        assertThrows(ReglaNegocioException.class, () -> service.consultarSesion(UUID.randomUUID()));
    }

    @Test
    void actualizaConLecturaBloqueada() {
        var s = sesion();
        service.actualizarSesion(docente, s.getId(), "Nuevo", null, inicio, fin);
        verify(repo).buscarParaActualizar(s.getId());
        assertEquals("Nuevo", s.getTitulo());
    }

    @Test
    void historialBloqueaCambios() {
        var s = sesion();
        when(repo.tieneParticipacionesVinculadas(s.getId())).thenReturn(true);
        assertThrows(
                ReglaNegocioException.class,
                () -> service.actualizarSesion(docente, s.getId(), "Nuevo", null, inicio, fin));
        verify(repo, never()).guardar(any());
    }

    @Test
    void docenteAjenoNoModifica() {
        var s = sesion();
        assertThrows(
                ReglaNegocioException.class,
                () ->
                        service.actualizarSesion(
                                UUID.randomUUID(), s.getId(), "Nuevo", null, inicio, fin));
        verify(repo, never()).guardar(any());
    }

    @Test
    void reasignacionProtegeConvocatorias() {
        var s = sesion();
        when(conv.listarPorSesion(s.getId()))
                .thenReturn(List.of(Convocatoria.crear(s.getId(), UUID.randomUUID())));
        assertThrows(
                ReglaNegocioException.class,
                () -> service.cambiarCaso(docente, s.getId(), UUID.randomUUID()));
        verify(repo, never()).guardar(any());
    }

    @Test
    void iniciaYFinalizaSinEstadosIntermediosInventados() {
        var s = sesion();
        service.cambiarEstado(docente, s.getId(), EstadoSesion.EN_CURSO);
        service.cambiarEstado(docente, s.getId(), EstadoSesion.FINALIZADO);
        assertEquals(EstadoSesion.FINALIZADO, s.getEstado());
    }

    @Test
    void convocaAntesDeIniciarSinConcederIntento() {
        var s = sesion();
        var c = service.convocarEstudiante(docente, s.getId(), UUID.randomUUID());
        assertEquals("Autorizado", c.getEstadoAutorizacion());
        verify(notificaciones).notificarConvocatoria(s.getId(), c.getEstudianteId());
    }

    @Test
    void noConvocaEnCurso() {
        var s = sesion();
        s.iniciar();
        assertThrows(
                ReglaNegocioException.class,
                () -> service.convocarEstudiante(docente, s.getId(), UUID.randomUUID()));
    }

    @Test
    void duplicacionDeConvocatoriaEsConflicto() {
        var s = sesion();
        var estudiante = UUID.randomUUID();
        when(conv.existeConvocatoria(s.getId(), estudiante)).thenReturn(true);
        assertThrows(
                ReglaNegocioException.class,
                () -> service.convocarEstudiante(docente, s.getId(), estudiante));
        verifyNoInteractions(notificaciones);
    }

    @Test
    void accesoBooleanoLegadoPermaneceRetirado() {
        assertThrows(
                ReglaNegocioException.class,
                () -> service.verificarAccesoSesion(UUID.randomUUID(), UUID.randomUUID()));
    }
}
