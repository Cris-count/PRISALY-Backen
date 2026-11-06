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
import com.prisaly.m5.application.ResumenIntento;
import com.prisaly.m5.application.RevisionObsoletaException;
import com.prisaly.m5.domain.intento.Intento;
import com.prisaly.m5.domain.intento.IntentoFinalizadoException;
import com.prisaly.m5.domain.intento.MotivoFinalizacion;
import com.prisaly.m5.domain.intento.RespuestaDuplicadaException;
import com.prisaly.m5.domain.respuesta.DatoRespuestaInvalidoException;
import com.prisaly.m5.domain.respuesta.ReferenciaPregunta;
import com.prisaly.m5.domain.respuesta.Respuesta;
import com.prisaly.m5.domain.respuesta.ValorRespuesta;

@DisplayName("RegistrarRespuestaServicio")
class RegistrarRespuestaServicioTest {

    private static final ReferenciaIntento REF = new ReferenciaIntento("I-1");
    private static final ReferenciaPregunta P1 = new ReferenciaPregunta("P-1");
    private static final ValorRespuesta A = new ValorRespuesta("Opción A");
    private static final ValorRespuesta B = new ValorRespuesta("Opción B");

    private RepositorioIntentosEnMemoria repositorio;
    private RegistrarRespuestaServicio servicio;
    private Intento intento;

    @BeforeEach
    void preparar() {
        repositorio = new RepositorioIntentosEnMemoria();
        servicio = new RegistrarRespuestaServicio(repositorio);
        intento = repositorio.existente(REF, Intento.iniciar());
    }

    @Test
    @DisplayName("registra una respuesta válida y la guarda una vez")
    void registraRespuestaValida() {
        ResumenIntento resumen = servicio.registrar(REF, P1, A);

        assertEquals(List.of(new Respuesta(P1, A)), resumen.respuestas());
        assertEquals(List.of(new Respuesta(P1, A)), intento.respuestas());
        assertEquals(1, repositorio.guardados());
    }

    @Test
    @DisplayName("CAS: cero exige ausencia, la revisión vigente avanza y una obsoleta no guarda")
    void casObsoletoNoGuardaNiCambiaLaRespuesta() {
        assertEquals(1, servicio.guardar(REF, P1, A, 0).revision());
        assertEquals(2, servicio.guardar(REF, P1, A, 1).revision());

        assertThrows(RevisionObsoletaException.class, () -> servicio.guardar(REF, P1, B, 1));

        assertEquals(2, repositorio.guardados());
        assertEquals(List.of(new Respuesta(P1, A, 2)), intento.respuestas());
    }

    @Test
    @DisplayName("CAS: un intento finalizado se rechaza con cualquier revisión y sin guardar")
    void casSobreIntentoFinalizado() {
        servicio.guardar(REF, P1, A, 0);
        intento.finalizar(com.prisaly.m5.domain.intento.MotivoFinalizacion.ENTREGA_ESTUDIANTE);

        assertThrows(com.prisaly.m5.domain.intento.IntentoFinalizadoException.class, () -> servicio.guardar(REF, P1, B, 1));
        assertThrows(com.prisaly.m5.domain.intento.IntentoFinalizadoException.class, () -> servicio.guardar(REF, P1, B, 7));
        assertEquals(1, repositorio.guardados());
    }

    @Test
    @DisplayName("CAS: una revisión negativa es un dato inválido")
    void casRevisionNegativa() {
        assertThrows(com.prisaly.m5.application.DatoAplicacionInvalidoException.class, () -> servicio.guardar(REF, P1, A, -1));
        assertEquals(0, repositorio.guardados());
    }

    @Test
    @DisplayName("propaga el rechazo de una respuesta duplicada sin guardar")
    void rechazaDuplicadaSinGuardar() {
        servicio.registrar(REF, P1, A);

        assertThrows(RespuestaDuplicadaException.class, () -> servicio.registrar(REF, P1, B));

        assertEquals(1, repositorio.guardados());
        assertEquals(List.of(new Respuesta(P1, A)), intento.respuestas());
    }

    @Test
    @DisplayName("propaga el rechazo en un intento finalizado sin guardar")
    void rechazaIntentoFinalizadoSinGuardar() {
        intento.finalizar(MotivoFinalizacion.TIEMPO_VENCIDO);

        assertThrows(IntentoFinalizadoException.class, () -> servicio.registrar(REF, P1, A));

        assertEquals(0, repositorio.guardados());
        assertEquals(List.of(), intento.respuestas());
    }

    @Test
    @DisplayName("propaga datos de respuesta inválidos sin guardar")
    void rechazaDatosInvalidosSinGuardar() {
        assertThrows(DatoRespuestaInvalidoException.class, () -> servicio.registrar(REF, null, A));
        assertThrows(DatoRespuestaInvalidoException.class, () -> servicio.registrar(REF, P1, null));

        assertEquals(0, repositorio.guardados());
    }

    @Test
    @DisplayName("informa un intento inexistente sin guardar")
    void rechazaIntentoInexistente() {
        assertThrows(IntentoNoEncontradoException.class,
                () -> servicio.registrar(new ReferenciaIntento("I-X"), P1, A));

        assertEquals(0, repositorio.guardados());
    }
}
