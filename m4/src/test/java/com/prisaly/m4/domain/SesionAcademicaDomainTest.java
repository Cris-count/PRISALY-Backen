package com.prisaly.m4.domain;

import static org.junit.jupiter.api.Assertions.*;

import com.prisaly.m4.domain.model.*;
import com.prisaly.m4.domain.rule.*;
import com.prisaly.m4.domain.state.*;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.time.Instant;
import java.util.UUID;

class SesionAcademicaDomainTest {
    private static final Instant INICIO = Instant.parse("2030-11-05T09:00:00Z"),
            FIN = INICIO.plusSeconds(3600);

    private SesionAcademica crear() {
        return SesionAcademica.crear(
                UUID.randomUUID(), UUID.randomUUID(), " Espacio ", "Mensaje", INICIO, FIN, 1800);
    }

    @Test
    void creaConDatosOficiales() {
        var s = crear();
        assertNotNull(s.getId());
        assertEquals("Espacio", s.getTitulo());
        assertEquals(EstadoSesion.SIN_INICIAR, s.getEstado());
        assertEquals(1800, s.getTiempoMaximoParticipacionSegundos());
    }

    @Test
    void fechasIgualesOInvertidasNoSonValidas() {
        for (var fin : new Instant[] {INICIO, INICIO.minusSeconds(1)})
            assertThrows(
                    ReglaNegocioException.class,
                    () ->
                            SesionAcademica.crear(
                                    UUID.randomUUID(),
                                    UUID.randomUUID(),
                                    "Espacio",
                                    null,
                                    INICIO,
                                    fin,
                                    1800));
    }

    @Test
    void duracionEsExplicitaYPositiva() {
        assertThrows(
                ReglaNegocioException.class,
                () ->
                        SesionAcademica.crear(
                                UUID.randomUUID(),
                                UUID.randomUUID(),
                                "Espacio",
                                null,
                                INICIO,
                                FIN,
                                0));
    }

    @ParameterizedTest
    @ValueSource(
            strings = {
                "",
                " ",
                "                                                                                  "
                    + "                                                                     x"
            })
    void nombreInvalidoNoSeAcepta(String nombre) {
        if (nombre.strip().equals("x")) nombre = "x".repeat(151);
        final String n = nombre;
        assertThrows(
                ReglaNegocioException.class,
                () ->
                        SesionAcademica.crear(
                                UUID.randomUUID(), UUID.randomUUID(), n, null, INICIO, FIN, 60));
    }

    @Test
    void stateRecorreSoloCatalogoOficial() {
        var s = crear();
        assertInstanceOf(SinIniciarState.class, SesionStateFactory.of(s.getEstado()));
        s.iniciar();
        assertEquals(EstadoSesion.EN_CURSO, s.getEstado());
        assertNotNull(s.getFechaInicioReal());
        s.cerrar();
        assertEquals(EstadoSesion.FINALIZADO, s.getEstado());
        assertNotNull(s.getFechaFinReal());
        assertFalse(s.getFechaFinReal().isBefore(s.getFechaInicioReal()));
    }

    @Test
    void noCierraSinIniciar() {
        assertThrows(SesionOperacionNoPermitidaException.class, () -> crear().cerrar());
    }

    @Test
    void noReabreFinalizado() {
        var s = crear();
        s.iniciar();
        s.cerrar();
        assertThrows(SesionOperacionNoPermitidaException.class, s::iniciar);
        assertThrows(SesionOperacionNoPermitidaException.class, s::cerrar);
    }

    @Test
    void noMapeaAperturaNiCancelacion() {
        var s = crear();
        assertThrows(SesionOperacionNoPermitidaException.class, s::abrir);
        assertThrows(SesionOperacionNoPermitidaException.class, s::cancelar);
        assertEquals(EstadoSesion.SIN_INICIAR, s.getEstado());
    }

    @ParameterizedTest
    @ValueSource(strings = {"PROGRAMADA", "ABIERTA", "CERRADA", "CANCELADA", "INVENTADO"})
    void noConvierteEstadosPropuestos(String valor) {
        assertThrows(IllegalArgumentException.class, () -> EstadoSesion.desdeBd(valor));
    }

    @Test
    void nombresPersistidosExactos() {
        assertEquals(3, EstadoSesion.values().length);
        for (var e : EstadoSesion.values()) assertSame(e, EstadoSesion.desdeBd(e.valorBd()));
    }

    @Test
    void programaSoloAntesDeIniciar() {
        var s = crear();
        s.actualizarProgramacion("Nuevo", null, INICIO, FIN);
        s.iniciar();
        assertThrows(
                SesionOperacionNoPermitidaException.class,
                () -> s.actualizarProgramacion("Otro", null, INICIO, FIN));
    }

    @Test
    void reasignaAntesDeIniciar() {
        var s = crear();
        var destino = UUID.randomUUID();
        s.cambiarCaso(destino);
        assertEquals(destino, s.getCasoId());
        s.iniciar();
        assertThrows(
                SesionOperacionNoPermitidaException.class, () -> s.cambiarCaso(UUID.randomUUID()));
    }

    @Test
    void convocatoriaNoEquivaleAAcceso() {
        var s = crear();
        assertTrue(s.permiteConvocatoria());
        s.iniciar();
        assertFalse(s.permiteConvocatoria());
    }

    @Test
    void convocatoriaConservaCatalogoSinBooleanoInventado() {
        var c = Convocatoria.crear(UUID.randomUUID(), UUID.randomUUID());
        assertEquals("Autorizado", c.getEstadoAutorizacion());
        assertNull(c.getFechaRetiro());
        assertThrows(ReglaNegocioException.class, c::confirmar);
    }
}
