package com.prisaly.m4.domain;

import com.prisaly.m4.api.ReglaNegocioException;
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
    /** Instantes fijos del reloj académico (C4): el dominio ya no lee la JVM. */
    private static final Instant T1 = Instant.parse("2030-11-05T09:01:02.123456Z"),
            T2 = T1.plusSeconds(1200);

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
        s.iniciar(T1);
        assertEquals(EstadoSesion.EN_CURSO, s.getEstado());
        assertEquals(T1, s.getFechaInicioReal());
        s.cerrar(T2);
        assertEquals(EstadoSesion.FINALIZADO, s.getEstado());
        assertEquals(T2, s.getFechaFinReal());
        assertFalse(s.getFechaFinReal().isBefore(s.getFechaInicioReal()));
    }

    @Test
    void noCierraSinIniciar() {
        assertThrows(SesionOperacionNoPermitidaException.class, () -> crear().cerrar(T2));
    }

    @Test
    void noReabreFinalizado() {
        var s = crear();
        s.iniciar(T1);
        s.cerrar(T2);
        assertThrows(SesionOperacionNoPermitidaException.class, () -> s.iniciar(T1));
        assertThrows(SesionOperacionNoPermitidaException.class, () -> s.cerrar(T2));
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
        s.iniciar(T1);
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
        s.iniciar(T1);
        assertThrows(
                SesionOperacionNoPermitidaException.class, () -> s.cambiarCaso(UUID.randomUUID()));
    }

    @Test
    void convocatoriaNoEquivaleAAcceso() {
        var s = crear();
        assertTrue(s.permiteConvocatoria());
        s.iniciar(T1);
        assertFalse(s.permiteConvocatoria());
    }

    @Test
    void convocatoriaConservaCatalogoSinBooleanoInventado() {
        var c = Convocatoria.crear(UUID.randomUUID(), UUID.randomUUID());
        assertEquals("Autorizado", c.getEstadoAutorizacion());
        assertNull(c.getFechaRetiro());
        assertThrows(ReglaNegocioException.class, c::confirmar);
    }

    /**
     * N2: abrir/cancelar have no callers and no ratified transition (the V010 states were not adopted). Every
     * official state must keep rejecting them without changing the session, so they cannot silently grant a path.
     */
    @ParameterizedTest
    @org.junit.jupiter.params.provider.EnumSource(EstadoSesion.class)
    void transicionesSinRatificarSiguenRechazadasEnTodoEstadoOficial(EstadoSesion estado) {
        var s = crear();
        var state = SesionStateFactory.of(estado);
        assertEquals(estado, state.getEstado());
        assertThrows(SesionOperacionNoPermitidaException.class, () -> state.abrir(s));
        assertThrows(SesionOperacionNoPermitidaException.class, () -> state.cancelar(s));
        assertEquals(EstadoSesion.SIN_INICIAR, s.getEstado());
        assertEquals(3, EstadoSesion.values().length, "the official catalog stays SinIniciar/EnCurso/Finalizado");
    }

    /** C4: el instante lo aporta el reloj académico y una fecha real existente nunca se sobrescribe. */
    @Test
    void fechaInicioRealExistenteNoSeSobrescribe() {
        var s = crear();
        Instant persistida = Instant.parse("2030-11-05T08:59:00Z");
        s.setFechaInicioReal(persistida);
        s.iniciar(T1);
        assertEquals(EstadoSesion.EN_CURSO, s.getEstado());
        assertEquals(persistida, s.getFechaInicioReal());
        s.cerrar(T2);
        assertEquals(persistida, s.getFechaInicioReal());
        assertEquals(T2, s.getFechaFinReal());
    }

    @Test
    void fechaFinRealExistenteNoSeSobrescribe() {
        var s = crear();
        s.iniciar(T1);
        Instant persistida = T1.plusSeconds(5);
        s.setFechaFinReal(persistida);
        s.cerrar(T2);
        assertEquals(persistida, s.getFechaFinReal());
        assertEquals(T1, s.getFechaInicioReal());
    }

    @Test
    void sinInstanteDelRelojNoHayTransicionNiMarca() {
        var s = crear();
        assertThrows(NullPointerException.class, () -> s.iniciar(null));
        assertEquals(EstadoSesion.SIN_INICIAR, s.getEstado());
        assertNull(s.getFechaInicioReal());
        // Los estados solo pueden cambiarla durante iniciar/cerrar: sin instante no hay marca JVM.
        assertThrows(IllegalStateException.class, () -> s.cambiarEstado(EnCursoState.INSTANCE));
        assertEquals(EstadoSesion.SIN_INICIAR, s.getEstado());
        assertNull(s.getFechaInicioReal());
        s.iniciar(T1);
        assertThrows(NullPointerException.class, () -> s.cerrar(null));
        assertEquals(EstadoSesion.EN_CURSO, s.getEstado());
        assertNull(s.getFechaFinReal());
    }

    @Test
    void unaTransicionRechazadaNoDejaInstantePendiente() {
        var s = crear();
        assertThrows(SesionOperacionNoPermitidaException.class, () -> s.cerrar(T2));
        assertThrows(IllegalStateException.class, () -> s.cambiarEstado(EnCursoState.INSTANCE));
        assertNull(s.getFechaFinReal());
    }
}
