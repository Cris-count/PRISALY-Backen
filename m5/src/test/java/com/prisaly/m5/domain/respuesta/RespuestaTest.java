package com.prisaly.m5.domain.respuesta;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

@DisplayName("Respuesta y sus valores")
class RespuestaTest {

    @Test
    @DisplayName("una respuesta conserva la pregunta y el valor con que se creó")
    void conservaPreguntaYValor() {
        ReferenciaPregunta pregunta = new ReferenciaPregunta("P-1");
        ValorRespuesta valor = new ValorRespuesta("Opción B");

        Respuesta respuesta = new Respuesta(pregunta, valor);

        assertEquals(pregunta, respuesta.pregunta());
        assertEquals(valor, respuesta.valor());
    }

    @Test
    @DisplayName("las referencias de pregunta se comparan por su valor")
    void referenciasSeComparanPorValor() {
        assertEquals(new ReferenciaPregunta("P-1"), new ReferenciaPregunta("P-1"));
        assertNotEquals(new ReferenciaPregunta("P-1"), new ReferenciaPregunta("P-2"));
    }

    @ParameterizedTest(name = "referencia de pregunta inválida: \"{0}\"")
    @NullAndEmptySource
    @ValueSource(strings = {"   ", "\t"})
    void rechazaReferenciaDePreguntaVacia(String valor) {
        assertThrows(DatoRespuestaInvalidoException.class, () -> new ReferenciaPregunta(valor));
    }

    @ParameterizedTest(name = "valor de respuesta inválido: \"{0}\"")
    @NullAndEmptySource
    @ValueSource(strings = {"   ", "\n"})
    void rechazaValorDeRespuestaVacio(String valor) {
        assertThrows(DatoRespuestaInvalidoException.class, () -> new ValorRespuesta(valor));
    }

    @Test
    @DisplayName("una respuesta exige pregunta y valor")
    void respuestaExigePreguntaYValor() {
        ReferenciaPregunta pregunta = new ReferenciaPregunta("P-1");
        ValorRespuesta valor = new ValorRespuesta("Opción B");

        assertThrows(DatoRespuestaInvalidoException.class, () -> new Respuesta(null, valor));
        assertThrows(DatoRespuestaInvalidoException.class, () -> new Respuesta(pregunta, null));
    }
}
