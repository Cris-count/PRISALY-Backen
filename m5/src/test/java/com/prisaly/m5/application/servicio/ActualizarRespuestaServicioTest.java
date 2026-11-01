package com.prisaly.m5.application.servicio;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.prisaly.m5.application.IntentoNoEncontradoException;
import com.prisaly.m5.application.ReferenciaIntento;
import com.prisaly.m5.application.RepositorioIntentosEnMemoria;
import com.prisaly.m5.domain.intento.Intento;
import com.prisaly.m5.domain.intento.IntentoFinalizadoException;
import com.prisaly.m5.domain.intento.MotivoFinalizacion;
import com.prisaly.m5.domain.intento.RespuestaNoRegistradaException;
import com.prisaly.m5.domain.respuesta.ReferenciaPregunta;
import com.prisaly.m5.domain.respuesta.Respuesta;
import com.prisaly.m5.domain.respuesta.ValorRespuesta;

/** Cubre una operación provisional, pendiente de ratificación académica. */
@DisplayName("ActualizarRespuestaServicio (provisional)")
class ActualizarRespuestaServicioTest {

    private static final ReferenciaIntento REF = new ReferenciaIntento("I-1");
    private static final ReferenciaPregunta P1 = new ReferenciaPregunta("P-1");
    private static final ReferenciaPregunta P2 = new ReferenciaPregunta("P-2");
    private static final ValorRespuesta A = new ValorRespuesta("Opción A");
    private static final ValorRespuesta B = new ValorRespuesta("Opción B");

    private RepositorioIntentosEnMemoria repositorio;
    private ActualizarRespuestaServicio servicio;
    private Intento intento;

    @BeforeEach
    void preparar() {
        repositorio = new RepositorioIntentosEnMemoria();
        servicio = new ActualizarRespuestaServicio(repositorio);
        intento = repositorio.existente(REF, Intento.iniciar());
        intento.registrarRespuesta(P1, A);
    }

    @Test
    @DisplayName("reemplaza la respuesta en curso y la guarda una vez")
    void actualizaRespuestaEnCurso() {
        assertEquals(List.of(new Respuesta(P1, B)), servicio.actualizar(REF, P1, B).respuestas());
        assertEquals(1, repositorio.guardados());
    }

    @Test
    @DisplayName("rechaza actualizar una pregunta sin respuesta previa, sin guardar")
    void rechazaSinRespuestaPrevia() {
        assertThrows(RespuestaNoRegistradaException.class, () -> servicio.actualizar(REF, P2, B));

        assertEquals(0, repositorio.guardados());
        assertEquals(List.of(new Respuesta(P1, A)), intento.respuestas());
    }

    @Test
    @DisplayName("rechaza actualizar en un intento finalizado, sin guardar")
    void rechazaIntentoFinalizado() {
        intento.finalizar(MotivoFinalizacion.ENTREGA_ESTUDIANTE);

        assertThrows(IntentoFinalizadoException.class, () -> servicio.actualizar(REF, P1, B));

        assertEquals(0, repositorio.guardados());
        assertEquals(List.of(new Respuesta(P1, A)), intento.respuestas());
    }

    @Test
    @DisplayName("informa un intento inexistente sin guardar")
    void rechazaIntentoInexistente() {
        assertThrows(IntentoNoEncontradoException.class,
                () -> servicio.actualizar(new ReferenciaIntento("I-X"), P1, B));

        assertEquals(0, repositorio.guardados());
    }
}
