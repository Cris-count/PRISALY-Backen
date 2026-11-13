package com.prisaly.m5.infrastructure.contenido;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import com.prisaly.m3.api.ContenidoCasoEstudiantilPort;
import com.prisaly.m4.api.ContextoProgramacion;
import com.prisaly.m4.api.ProgramacionParticipacionPort;
import com.prisaly.m5.application.*;
import com.prisaly.m5.application.acceso.*;
import com.prisaly.m5.application.port.RepositorioIntentos;
import java.util.*;
import java.time.*;
import org.junit.jupiter.api.Test;

class ConsultaContenidoIntentoTest {
    final UUID estudiante=UUID.randomUUID(), convocatoria=UUID.randomUUID(), caso=UUID.randomUUID(), sesion=UUID.randomUUID();
    final ReferenciaIntento intento=new ReferenciaIntento(UUID.randomUUID().toString());
    final IdentidadEstudianteActual identidad=mock(IdentidadEstudianteActual.class);
    final TitularidadIntentos titularidad=mock(TitularidadIntentos.class);
    final RepositorioIntentos repositorio=mock(RepositorioIntentos.class);
    final ProgramacionParticipacionPort m4=mock(ProgramacionParticipacionPort.class);
    final ContenidoCasoEstudiantilPort m3=mock(ContenidoCasoEstudiantilPort.class);
    final ConsultaContenidoIntentoTransaccional servicio=new ConsultaContenidoIntentoTransaccional(identidad,titularidad,repositorio,m4,m3);
    void preparar() {
        when(identidad.actual()).thenReturn(Optional.of(new IdentificadorEstudiante(estudiante.toString())));
        when(titularidad.de(any(),any())).thenReturn(Titularidad.PROPIO);
        when(repositorio.vinculo(intento)).thenReturn(Optional.of(new RepositorioIntentos.VinculoParticipacion(UUID.randomUUID(),convocatoria)));
        when(m4.consultarConvocatoria(convocatoria,estudiante)).thenReturn(Optional.of(contexto(estudiante)));
        when(m3.consultar(caso)).thenReturn(Optional.of(new ContenidoCasoEstudiantilPort.Caso(caso,"Caso",null,null,List.of())));
    }
    ContextoProgramacion contexto(UUID titular) {
        Instant t=Instant.parse("2026-11-13T12:00:00Z");
        return new ContextoProgramacion(convocatoria,titular,sesion,caso,"Programada","SinAutorizacion",Optional.empty(),"Publicado",true,t,t.plusSeconds(600),Optional.empty(),Optional.empty(),Duration.ofSeconds(600),Duration.ofSeconds(600),t);
    }
    @Test void sinIdentidadNoCargaNingunContenido() {
        when(identidad.actual()).thenReturn(Optional.empty());
        assertThrows(EstudianteNoIdentificadoException.class,()->servicio.consultar(intento));
        verifyNoInteractions(titularidad,repositorio,m4,m3);
    }
    @Test void ajenoNoCargaNingunContenido() {
        preparar(); when(titularidad.de(any(),any())).thenReturn(Titularidad.AJENO);
        assertThrows(AccesoIntentoDenegadoException.class,()->servicio.consultar(intento));
        verifyNoInteractions(repositorio,m4,m3);
    }
    @Test void resuelveSoloElCasoDeLaProgramacionReal() {
        preparar(); var dto=servicio.consultar(intento);
        assertEquals(sesion,dto.idSesion()); assertEquals(caso,dto.caso().idCaso());
        verify(m4).consultarConvocatoria(convocatoria,estudiante); verify(m3).consultar(caso);
        verify(repositorio,never()).guardar(any(),any());
        assertThrows(UnsupportedOperationException.class,()->dto.caso().escenarios().clear());
    }
    @Test void contextoAjenoFallaAntesDeM3() {
        preparar(); when(m4.consultarConvocatoria(convocatoria,estudiante)).thenReturn(Optional.of(contexto(UUID.randomUUID())));
        assertThrows(PersistenciaIntentoException.class,()->servicio.consultar(intento)); verifyNoInteractions(m3);
    }
    @Test void contenidoInconsistenteNoSeEntrega() {
        preparar(); when(m3.consultar(caso)).thenReturn(Optional.of(new ContenidoCasoEstudiantilPort.Caso(UUID.randomUUID(),"Otro",null,null,List.of())));
        assertThrows(PersistenciaIntentoException.class,()->servicio.consultar(intento));
    }
    @Test void contenidoNoDisponibleNoSeConvierteEnExito() {
        preparar(); when(m3.consultar(caso)).thenReturn(Optional.empty());
        assertThrows(ContenidoIntentoNoDisponibleException.class,()->servicio.consultar(intento));
    }
    @Test void vinculoDesaparecidoNoPermiteLeerM4NiM3() {
        preparar(); when(repositorio.vinculo(intento)).thenReturn(Optional.empty());
        assertThrows(IntentoNoEncontradoException.class,()->servicio.consultar(intento));
        verifyNoInteractions(m4,m3);
    }
}
