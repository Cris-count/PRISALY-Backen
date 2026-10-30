package com.prisaly.m5.domain.intento;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.prisaly.m5.domain.respuesta.DatoRespuestaInvalidoException;
import com.prisaly.m5.domain.respuesta.ReferenciaPregunta;
import com.prisaly.m5.domain.respuesta.Respuesta;
import com.prisaly.m5.domain.respuesta.ValorRespuesta;

/**
 * Cubre la actualización de respuestas, una decisión de diseño provisional
 * pendiente de ratificación académica. Si se descarta, estas pruebas se
 * retiran junto con {@link Intento#actualizarRespuesta}.
 */
@DisplayName("Intento: actualización provisional de respuestas")
class IntentoActualizacionRespuestasTest {

    private static final ReferenciaPregunta P1 = new ReferenciaPregunta("P-1");
    private static final ReferenciaPregunta P2 = new ReferenciaPregunta("P-2");
    private static final ValorRespuesta A = new ValorRespuesta("Opción A");
    private static final ValorRespuesta B = new ValorRespuesta("Opción B");
    private static final ValorRespuesta C = new ValorRespuesta("Opción C");

    private Intento intento;

    @BeforeEach
    void iniciarIntento() {
        intento = Intento.iniciar();
    }

    @Test
    @DisplayName("reemplaza la respuesta de una pregunta mientras el intento está EnCurso")
    void reemplazaRespuestaEnCurso() {
        intento.registrarRespuesta(P1, A);

        Respuesta actualizada = intento.actualizarRespuesta(P1, B);

        assertEquals(new Respuesta(P1, B), actualizada);
        assertEquals(Optional.of(actualizada), intento.respuestaA(P1));
    }

    @Test
    @DisplayName("la actualización no duplica la pregunta y conserva el orden")
    void actualizacionNoDuplicaYConservaOrden() {
        intento.registrarRespuesta(P1, A);
        intento.registrarRespuesta(P2, B);

        intento.actualizarRespuesta(P1, C);

        assertEquals(List.of(new Respuesta(P1, C), new Respuesta(P2, B)), intento.respuestas());
    }

    @Test
    @DisplayName("rechaza actualizar una pregunta sin respuesta previa")
    void rechazaActualizarSinRespuestaPrevia() {
        intento.registrarRespuesta(P1, A);

        RespuestaNoRegistradaException error =
                assertThrows(RespuestaNoRegistradaException.class, () -> intento.actualizarRespuesta(P2, B));

        assertEquals(P2, error.pregunta());
        assertEquals(List.of(new Respuesta(P1, A)), intento.respuestas());
    }

    @Test
    @DisplayName("rechaza actualizar después de finalizar y conserva la respuesta original")
    void rechazaActualizarDespuesDeFinalizar() {
        intento.registrarRespuesta(P1, A);
        intento.finalizar(MotivoFinalizacion.TIEMPO_VENCIDO);

        IntentoFinalizadoException error =
                assertThrows(IntentoFinalizadoException.class, () -> intento.actualizarRespuesta(P1, B));

        assertEquals("actualizar una respuesta", error.operacion());
        assertEquals(List.of(new Respuesta(P1, A)), intento.respuestas());
        assertEquals(Optional.of(MotivoFinalizacion.TIEMPO_VENCIDO), intento.motivoFinalizacion());
    }

    @Test
    @DisplayName("en un intento Finalizado se rechaza primero por estado, aunque la pregunta no exista")
    void finalizadoTienePrioridadSobreRespuestaInexistente() {
        intento.finalizar(MotivoFinalizacion.ENTREGA_ESTUDIANTE);

        assertThrows(IntentoFinalizadoException.class, () -> intento.actualizarRespuesta(P1, A));
    }

    @Test
    @DisplayName("rechaza argumentos nulos sin alterar la respuesta registrada")
    void rechazaArgumentosNulos() {
        intento.registrarRespuesta(P1, A);

        assertThrows(DatoRespuestaInvalidoException.class, () -> intento.actualizarRespuesta(null, B));
        assertThrows(DatoRespuestaInvalidoException.class, () -> intento.actualizarRespuesta(P1, null));

        assertEquals(List.of(new Respuesta(P1, A)), intento.respuestas());
    }

    @Test
    @DisplayName("tras una actualización, la siguiente finalización bloquea nuevos cambios")
    void finalizarTrasActualizarBloqueaCambios() {
        intento.registrarRespuesta(P1, A);
        intento.actualizarRespuesta(P1, B);
        intento.finalizar(MotivoFinalizacion.ENTREGA_ESTUDIANTE);

        assertThrows(IntentoFinalizadoException.class, () -> intento.actualizarRespuesta(P1, C));
        assertThrows(IntentoFinalizadoException.class, () -> intento.registrarRespuesta(P2, C));

        assertEquals(List.of(new Respuesta(P1, B)), intento.respuestas());
        assertEquals(EstadoIntento.FINALIZADO, intento.estado());
    }
}
