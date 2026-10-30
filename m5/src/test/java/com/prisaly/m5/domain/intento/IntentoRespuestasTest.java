package com.prisaly.m5.domain.intento;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.prisaly.m5.domain.respuesta.DatoRespuestaInvalidoException;
import com.prisaly.m5.domain.respuesta.ReferenciaPregunta;
import com.prisaly.m5.domain.respuesta.Respuesta;
import com.prisaly.m5.domain.respuesta.ValorRespuesta;

@DisplayName("Intento: registro y consulta de respuestas")
class IntentoRespuestasTest {

    private static final ReferenciaPregunta P1 = new ReferenciaPregunta("P-1");
    private static final ReferenciaPregunta P2 = new ReferenciaPregunta("P-2");
    private static final ValorRespuesta A = new ValorRespuesta("Opción A");
    private static final ValorRespuesta B = new ValorRespuesta("Opción B");

    private Intento intento;

    @BeforeEach
    void iniciarIntento() {
        intento = Intento.iniciar();
    }

    @Test
    @DisplayName("un intento nuevo no tiene respuestas")
    void intentoNuevoSinRespuestas() {
        assertTrue(intento.respuestas().isEmpty());
        assertEquals(Optional.empty(), intento.respuestaA(P1));
    }

    @Test
    @DisplayName("registra una respuesta válida mientras el intento está EnCurso")
    void registraRespuestaValida() {
        Respuesta registrada = intento.registrarRespuesta(P1, A);

        assertEquals(new Respuesta(P1, A), registrada);
        assertEquals(Optional.of(registrada), intento.respuestaA(P1));
        assertEquals(EstadoIntento.EN_CURSO, intento.estado());
    }

    @Test
    @DisplayName("consulta las respuestas en el orden en que se registraron")
    void consultaRespuestasEnOrden() {
        intento.registrarRespuesta(P2, B);
        intento.registrarRespuesta(P1, A);

        assertEquals(List.of(new Respuesta(P2, B), new Respuesta(P1, A)), intento.respuestas());
    }

    @Test
    @DisplayName("la lista consultada no permite modificar las respuestas del intento")
    void listaConsultadaEsInmodificable() {
        intento.registrarRespuesta(P1, A);
        List<Respuesta> consultadas = intento.respuestas();

        assertThrows(UnsupportedOperationException.class, () -> consultadas.add(new Respuesta(P2, B)));
        assertThrows(UnsupportedOperationException.class, consultadas::clear);
        assertEquals(List.of(new Respuesta(P1, A)), intento.respuestas());
    }

    @Test
    @DisplayName("rechaza una segunda respuesta para la misma pregunta sin duplicarla")
    void rechazaRespuestaDuplicada() {
        intento.registrarRespuesta(P1, A);

        RespuestaDuplicadaException error =
                assertThrows(RespuestaDuplicadaException.class, () -> intento.registrarRespuesta(P1, B));

        assertEquals(P1, error.pregunta());
        assertEquals(List.of(new Respuesta(P1, A)), intento.respuestas());
    }

    @Test
    @DisplayName("una referencia igual por valor cuenta como la misma pregunta")
    void referenciaIgualPorValorEsLaMismaPregunta() {
        intento.registrarRespuesta(new ReferenciaPregunta("P-1"), A);

        assertThrows(RespuestaDuplicadaException.class,
                () -> intento.registrarRespuesta(new ReferenciaPregunta("P-1"), B));
        assertEquals(1, intento.respuestas().size());
    }

    @Test
    @DisplayName("rechaza registrar respuestas después de finalizar y conserva las existentes")
    void rechazaRegistroDespuesDeFinalizar() {
        intento.registrarRespuesta(P1, A);
        intento.finalizar(MotivoFinalizacion.TIEMPO_VENCIDO);

        IntentoFinalizadoException error =
                assertThrows(IntentoFinalizadoException.class, () -> intento.registrarRespuesta(P2, B));

        assertEquals("registrar una respuesta", error.operacion());
        assertEquals(List.of(new Respuesta(P1, A)), intento.respuestas());
        assertEquals(EstadoIntento.FINALIZADO, intento.estado());
    }

    @Test
    @DisplayName("rechaza argumentos nulos sin alterar el intento")
    void rechazaArgumentosNulos() {
        intento.registrarRespuesta(P1, A);

        assertThrows(DatoRespuestaInvalidoException.class, () -> intento.registrarRespuesta(null, B));
        assertThrows(DatoRespuestaInvalidoException.class, () -> intento.registrarRespuesta(P2, null));
        assertThrows(DatoRespuestaInvalidoException.class, () -> intento.respuestaA(null));

        assertEquals(List.of(new Respuesta(P1, A)), intento.respuestas());
        assertEquals(EstadoIntento.EN_CURSO, intento.estado());
    }

    @Test
    @DisplayName("las respuestas siguen consultables después de finalizar")
    void respuestasConsultablesTrasFinalizar() {
        intento.registrarRespuesta(P1, A);
        intento.finalizar(MotivoFinalizacion.ENTREGA_ESTUDIANTE);

        assertEquals(Optional.of(new Respuesta(P1, A)), intento.respuestaA(P1));
        assertEquals(List.of(new Respuesta(P1, A)), intento.respuestas());
    }
}
