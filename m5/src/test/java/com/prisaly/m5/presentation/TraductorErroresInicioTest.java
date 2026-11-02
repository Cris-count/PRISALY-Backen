package com.prisaly.m5.presentation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.prisaly.m5.application.ConvocatoriaNoEncontradaException;
import com.prisaly.m5.application.RepeticionNoHabilitadaException;
import com.prisaly.m5.application.inicio.DecisionPendiente;
import com.prisaly.m5.application.inicio.InicioNoAutorizadoException;
import com.prisaly.m5.application.inicio.InicioNoDisponibleException;

/** Códigos estables (PROVISIONALES hasta ARQ-008.3) de iniciar o reanudar. */
@DisplayName("TraductorErroresM5: iniciar o reanudar")
class TraductorErroresInicioTest {

    private static TraductorErroresM5.ErrorHttp traducir(Throwable error) {
        return TraductorErroresM5.traducir(error).orElseThrow();
    }

    @Test
    @DisplayName("convocatoria no disponible → 404 sin revelar si existe")
    void convocatoria() {
        TraductorErroresM5.ErrorHttp http = traducir(new ConvocatoriaNoEncontradaException());
        assertEquals(404, http.estado());
        assertEquals("CONVOCATORIA_NO_ENCONTRADA", http.cuerpo().code());
    }

    @Test
    @DisplayName("decisión pendiente → 409 INICIO_NO_DISPONIBLE, el mensaje dice qué falta")
    void decisionPendiente() {
        for (DecisionPendiente decision : DecisionPendiente.values()) {
            TraductorErroresM5.ErrorHttp http = traducir(new InicioNoDisponibleException(decision));
            assertEquals(409, http.estado());
            assertEquals("INICIO_NO_DISPONIBLE", http.cuerpo().code());
            assertTrue(http.cuerpo().message().contains(decision.descripcion()), decision.name());
        }
    }

    @Test
    @DisplayName("no elegible → 403 INICIO_NO_AUTORIZADO")
    void noAutorizado() {
        TraductorErroresM5.ErrorHttp http = traducir(new InicioNoAutorizadoException());
        assertEquals(403, http.estado());
        assertEquals("INICIO_NO_AUTORIZADO", http.cuerpo().code());
    }

    @Test
    @DisplayName("repetición → 409 REPETICION_NO_HABILITADA, sin detalles de la base")
    void repeticion() {
        TraductorErroresM5.ErrorHttp http = traducir(new RepeticionNoHabilitadaException());
        assertEquals(409, http.estado());
        assertEquals("REPETICION_NO_HABILITADA", http.cuerpo().code());
        assertFalse(http.cuerpo().message().contains("tr_intento"));
    }
}
