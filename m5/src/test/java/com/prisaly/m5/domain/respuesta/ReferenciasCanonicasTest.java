package com.prisaly.m5.domain.respuesta;

import static org.junit.jupiter.api.Assertions.*;

import com.prisaly.m5.domain.intento.Intento;
import com.prisaly.m5.domain.intento.RespuestaDuplicadaException;
import org.junit.jupiter.api.Test;

/** DC-M5M6-MACRO-02: the same uuid in another letter case is the same reference; opaque values stay untouched. */
class ReferenciasCanonicasTest {
    private static final String MINUSCULAS = "3f2504e0-4f89-41d3-9a0c-0305e82c3301";

    @Test void elMismoUuidEnMayusculasEsLaMismaReferencia() {
        assertEquals(new ReferenciaPregunta(MINUSCULAS), new ReferenciaPregunta(MINUSCULAS.toUpperCase()));
        assertEquals(new ValorRespuesta(MINUSCULAS), new ValorRespuesta(MINUSCULAS.toUpperCase()));
    }

    @Test void elAgregadoDetectaElDuplicadoAunqueCambieLaCaja() {
        Intento intento = Intento.iniciar();
        intento.registrarRespuesta(new ReferenciaPregunta(MINUSCULAS), new ValorRespuesta("a"));
        assertTrue(intento.respuestaA(new ReferenciaPregunta(MINUSCULAS.toUpperCase())).isPresent());
        assertThrows(RespuestaDuplicadaException.class, () -> intento.registrarRespuesta(
                new ReferenciaPregunta(MINUSCULAS.toUpperCase()), new ValorRespuesta("b")));
    }

    @Test void losValoresOpacosNoSeAlteran() {
        assertEquals("Pregunta-A", new ReferenciaPregunta("Pregunta-A").valor());
        assertEquals("Texto LIBRE", new ValorRespuesta("Texto LIBRE").valor());
    }
}
