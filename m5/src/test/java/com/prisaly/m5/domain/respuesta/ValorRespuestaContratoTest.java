package com.prisaly.m5.domain.respuesta;

import static org.junit.jupiter.api.Assertions.*;

import com.prisaly.m5.domain.intento.Intento;
import org.junit.jupiter.api.Test;

/** Review of DC-M5M6-MACRO-02: canonicalization only touches a complete uuid; anything else is preserved exactly. */
class ValorRespuestaContratoTest {
    private static final String UUID_MIN = "3f2504e0-4f89-41d3-9a0c-0305e82c3301";

    @Test void cualquierTextoQueNoSeaUnUuidCompletoSeConservaExacto() {
        for (String texto : new String[] {"Texto LIBRE con Mayúsculas", "ÑANDÚ", "OPCION-A", " " + UUID_MIN.toUpperCase(),
                UUID_MIN.toUpperCase() + " ", "{" + UUID_MIN.toUpperCase() + "}", UUID_MIN.toUpperCase().substring(1),
                UUID_MIN.toUpperCase().replace("-", ""), UUID_MIN.toUpperCase() + "\nX", "ABCDEFGH-ABCD-ABCD-ABCD-ABCDEFGHIJKL"}) {
            assertEquals(texto, new ValorRespuesta(texto).valor());
        }
    }

    @Test void soloCambiaLaCajaDeUnUuidCompletoYElValorSigueSiendoElMismoUuid() {
        var v = new ValorRespuesta(UUID_MIN.toUpperCase());
        assertEquals(UUID_MIN, v.valor());
        assertEquals(java.util.UUID.fromString(UUID_MIN.toUpperCase()), java.util.UUID.fromString(v.valor()));
        assertEquals(new ValorRespuesta(UUID_MIN), new ValorRespuesta(new ValorRespuesta(UUID_MIN).valor()));
    }

    @Test void laMismaOpcionEnOtraCajaNoFabricaUnaRevisionNueva() {
        Intento intento = Intento.iniciar();
        var pregunta = new ReferenciaPregunta("P-1");
        intento.registrarRespuesta(pregunta, new ValorRespuesta(UUID_MIN));
        assertEquals(1, intento.actualizarRespuesta(pregunta, new ValorRespuesta(UUID_MIN.toUpperCase())).revision());
    }
}
