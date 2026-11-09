package com.prisaly.m5.application.servicio;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.Optional;

import org.junit.jupiter.api.Test;

import com.prisaly.m5.application.ReferenciaIntento;
import com.prisaly.m5.application.ResumenIntento;
import com.prisaly.m5.application.ResumenIntento.TiemposPersistidos;
import com.prisaly.m5.application.port.RepositorioIntentos;
import com.prisaly.m5.domain.intento.Intento;
import com.prisaly.m5.domain.respuesta.ReferenciaPregunta;
import com.prisaly.m5.domain.respuesta.ValorRespuesta;

/** Contrato de salida: responder no debe perder los tiempos que entrega la persistencia. */
class RespuestasTiemposPersistidosTest {
    private final ReferenciaIntento referencia = new ReferenciaIntento("I-1");
    private final ReferenciaPregunta pregunta = new ReferenciaPregunta("P-1");
    private final ValorRespuesta opcion = new ValorRespuesta("O-1");
    private final Intento intento = Intento.iniciar();
    private final RepositorioIntentos repositorio = mock(RepositorioIntentos.class);
    private final TiemposPersistidos tiempos = new TiemposPersistidos(
            Instant.parse("2026-11-08T12:00:00Z"), Instant.parse("2026-11-08T13:00:00Z"), null);
    private boolean guardado;

    private void preparar() {
        when(repositorio.buscar(referencia)).thenReturn(Optional.of(intento));
        doAnswer(invocacion -> { guardado = true; return null; })
                .when(repositorio).guardar(referencia, intento);
        when(repositorio.resumir(any(), any())).thenAnswer(invocacion -> {
            assertEquals(true, guardado, "El resumen debe obtenerse después de guardar");
            return new ResumenIntento(referencia, intento.estado(), intento.motivoFinalizacion(),
                    intento.respuestas(), Optional.of(tiempos));
        });
    }

    @Test
    void registrarConservaTiemposYRespuestaGuardada() {
        preparar();
        var resumen = new RegistrarRespuestaServicio(repositorio).registrar(referencia, pregunta, opcion);
        assertEquals(Optional.of(tiempos), resumen.tiempos());
        assertEquals(intento.respuestas(), resumen.respuestas());
    }

    @Test
    void actualizarConservaTiemposYRevisionGuardada() {
        intento.registrarRespuesta(pregunta, opcion);
        preparar();
        var resumen = new ActualizarRespuestaServicio(repositorio)
                .actualizar(referencia, pregunta, new ValorRespuesta("O-2"));
        assertEquals(Optional.of(tiempos), resumen.tiempos());
        assertEquals(2, resumen.respuestas().getFirst().revision());
    }
}
