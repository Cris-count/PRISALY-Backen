package com.prisaly.m6.application.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.prisaly.m6.api.SolicitudEvaluacion;
import com.prisaly.m6.application.AccesoEvaluacionDenegadoException;
import com.prisaly.m6.application.EvaluacionNoDisponibleException;
import com.prisaly.m6.application.integration.IntentoEvaluacion.RespuestaReferencia;
import com.prisaly.m6.application.port.AutorizacionEvaluacionPort;
import com.prisaly.m6.application.port.AutorizacionEvaluacionPort.DecisionAcceso;
import com.prisaly.m6.application.port.RegistroCalificacionPort;
import com.prisaly.m6.application.proxy.EvaluacionProtectionProxy;
import com.prisaly.m6.domain.model.Calificacion;
import com.prisaly.m6.domain.strategy.EntradaEvaluacion;
import com.prisaly.m6.domain.strategy.EstrategiaEvaluacion;
import com.prisaly.m6.domain.strategy.ResultadoEvaluacion;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

/** Orchestration only. The strategies here are test doubles: no formula, scale or rounding is implemented. */
class EvaluarIntentoServiceTest {
    final ContextoPreparadoPrueba contexto = new ContextoPreparadoPrueba();
    final SolicitudEvaluacion solicitud = contexto.solicitud();
    final ResultadoEvaluacion resultado = new ResultadoEvaluacion(new BigDecimal("3.50"), "{\"prueba\":true}");
    final PrepararEvaluacionProtegida preparacion = mock(PrepararEvaluacionProtegida.class);
    final RegistroCalificacionPort registro = mock(RegistroCalificacionPort.class);
    final EstrategiaEvaluacion estrategia = mock(EstrategiaEvaluacion.class);

    private Calificacion grabada() {
        return new Calificacion(UUID.randomUUID(), solicitud.idIntento(), solicitud.idRubrica(), resultado.valor(),
                Instant.parse("2026-11-12T00:00:00Z"), resultado.detalleEvaluacion(), "estrategia-prueba");
    }

    @Test
    void sinEstrategiaSeleccionadaLaEvaluacionOficialQuedaBloqueadaSinLeerNiEscribir() {
        for (String seleccion : new String[] {null, "", "  "}) {
            var servicio = new EvaluarIntentoService(preparacion, Map.of("estrategia-prueba", estrategia), seleccion, registro);
            var error = assertThrows(EvaluacionNoDisponibleException.class, () -> servicio.evaluar(solicitud));
            assertEquals("ESTRATEGIA_NO_RATIFICADA", error.codigo());
        }
        var sinDisponibles = new EvaluarIntentoService(preparacion, Map.of(), "estrategia-prueba", registro);
        assertThrows(EvaluacionNoDisponibleException.class, () -> sinDisponibles.evaluar(solicitud));
        verifyNoInteractions(preparacion, estrategia, registro);
    }

    @Test
    void laEstrategiaSeleccionadaRecibeLaEntradaDelContextoPreparadoYElResultadoSeRegistraUnaVez() {
        when(preparacion.preparar(solicitud)).thenReturn(contexto.preparado());
        when(estrategia.evaluar(any())).thenReturn(resultado);
        when(registro.registrar(solicitud.idIntento(), solicitud.idRubrica(), resultado, "estrategia-prueba"))
                .thenReturn(Optional.of(grabada()));
        var otra = mock(EstrategiaEvaluacion.class);
        var servicio = new EvaluarIntentoService(preparacion,
                Map.of("estrategia-prueba", estrategia, "otra", otra), " estrategia-prueba ", registro);
        assertSame(resultado, servicio.evaluar(solicitud));
        var entrada = ArgumentCaptor.forClass(EntradaEvaluacion.class);
        var orden = inOrder(preparacion, estrategia, registro);
        orden.verify(preparacion).preparar(solicitud);
        orden.verify(estrategia).evaluar(entrada.capture());
        orden.verify(registro).registrar(solicitud.idIntento(), solicitud.idRubrica(), resultado, "estrategia-prueba");
        verifyNoInteractions(otra);
        // C2: the strategy receives the prepared context, not two bare identifiers.
        assertEquals(solicitud, entrada.getValue().solicitud());
        assertEquals(contexto.caso, entrada.getValue().idCaso());
        assertEquals(3, entrada.getValue().preguntas().size());
        assertEquals(Optional.of(contexto.opcion1b), entrada.getValue().preguntas().get(0).opcionSeleccionada());
        assertEquals(Optional.of(contexto.definicion), entrada.getValue().definicionEvaluacionRubrica());
    }

    @Test
    void unaPreparacionRechazadaNoEjecutaLaEstrategiaNiEscribe() {
        doThrow(new AccesoEvaluacionDenegadoException()).when(preparacion).preparar(solicitud);
        var servicio = new EvaluarIntentoService(preparacion, Map.of("e", estrategia), "e", registro);
        assertThrows(AccesoEvaluacionDenegadoException.class, () -> servicio.evaluar(solicitud));
        doThrow(new IllegalStateException("El intento ya tiene calificacion")).when(preparacion).preparar(solicitud);
        assertThrows(IllegalStateException.class, () -> servicio.evaluar(solicitud));
        verifyNoInteractions(estrategia, registro);
    }

    @Test
    void unContextoIncoherenteNoEjecutaLaEstrategiaNiEscribe() {
        when(preparacion.preparar(solicitud)).thenReturn(contexto.preparado(
                List.of(new RespuestaReferencia(contexto.pregunta1, contexto.opcion3a)), contexto.definicion, Set.of()));
        var servicio = new EvaluarIntentoService(preparacion, Map.of("e", estrategia), "e", registro);
        var error = assertThrows(IllegalStateException.class, () -> servicio.evaluar(solicitud));
        assertTrue(error.getMessage().startsWith("Contexto de evaluacion incoherente"), error.getMessage());
        when(preparacion.preparar(solicitud)).thenReturn(null);
        assertThrows(NullPointerException.class, () -> servicio.evaluar(solicitud));
        verifyNoInteractions(estrategia, registro);
    }

    @Test
    void unaCalificacionConcurrenteNoSeSobrescribeYLaLlamadaFalla() {
        when(preparacion.preparar(solicitud)).thenReturn(contexto.preparado());
        when(estrategia.evaluar(any())).thenReturn(resultado);
        when(registro.registrar(any(), any(), any(), any())).thenReturn(Optional.empty());
        var servicio = new EvaluarIntentoService(preparacion, Map.of("e", estrategia), "e", registro);
        assertThrows(IllegalStateException.class, () -> servicio.evaluar(solicitud));
        verify(registro, times(1)).registrar(solicitud.idIntento(), solicitud.idRubrica(), resultado, "e");
    }

    @Test
    void unaEstrategiaSinResultadoNoEscribeNiInventaNota() {
        when(preparacion.preparar(solicitud)).thenReturn(contexto.preparado());
        var servicio = new EvaluarIntentoService(preparacion, Map.of("e", estrategia), "e", registro);
        assertThrows(NullPointerException.class, () -> servicio.evaluar(solicitud));
        verify(estrategia).evaluar(any());
        verifyNoInteractions(registro);
    }

    @Test
    void elIdentificadorDebeCaberEnVersionPoliticaYLasDependenciasSonObligatorias() {
        assertThrows(IllegalArgumentException.class,
                () -> new EvaluarIntentoService(preparacion, Map.of(), "x".repeat(51), registro));
        assertThrows(NullPointerException.class, () -> new EvaluarIntentoService(null, Map.of(), "", registro));
        assertThrows(NullPointerException.class, () -> new EvaluarIntentoService(preparacion, null, "", registro));
        assertThrows(NullPointerException.class, () -> new EvaluarIntentoService(preparacion, Map.of(), "", null));
    }

    @Test
    void comoSujetoRealDelProxyNoSeEjecutaSinAutorizacion() {
        AutorizacionEvaluacionPort denegada = s -> DecisionAcceso.DENEGADO;
        var servicio = new EvaluarIntentoService(preparacion, Map.of("e", estrategia), "e", registro);
        var proxy = new EvaluacionProtectionProxy(denegada, servicio);
        assertThrows(AccesoEvaluacionDenegadoException.class, () -> proxy.evaluar(solicitud));
        verifyNoInteractions(preparacion, estrategia, registro);
    }
}
