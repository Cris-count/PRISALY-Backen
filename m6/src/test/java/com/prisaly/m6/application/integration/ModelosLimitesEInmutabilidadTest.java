package com.prisaly.m6.application.integration;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.prisaly.m6.application.PreparacionContextoException;
import com.prisaly.m6.application.PreparacionContextoException.Codigo;
import com.prisaly.m6.application.integration.ContextoAcademico.Escenario;
import com.prisaly.m6.application.integration.ContextoAcademico.Pregunta;
import com.prisaly.m6.application.integration.ContextoAcademico.Rubrica;
import com.prisaly.m6.application.integration.ContextoAcademico.RubricaPregunta;
import com.prisaly.m6.application.integration.IntentoEvaluacion.EstadoIntento;
import com.prisaly.m6.application.integration.IntentoEvaluacion.RespuestaReferencia;
import com.prisaly.m6.application.port.ConsultaContextoAcademicoPort;
import com.prisaly.m6.application.port.ConsultaIntentoEvaluacionPort;
import com.prisaly.m6.application.port.SolicitudCapacidadContextoAcademicoPort;
import com.prisaly.m6.application.service.PrepararContextoEvaluacionService;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** DC-M5M6-MACRO-02: boundaries and defensive copies not pinned by ContratosConsumidoresTest. */
class ModelosLimitesEInmutabilidadTest {
    private final UUID caso = UUID.randomUUID(), rubrica = UUID.randomUUID(), pregunta = UUID.randomUUID(), escenario = UUID.randomUUID();

    @Test void limitesExactosDeNumericYTexto() {
        assertDoesNotThrow(() -> asociacion(new BigDecimal("999999.99")));
        assertDoesNotThrow(() -> asociacion(new BigDecimal("0.01")));
        assertDoesNotThrow(() -> asociacion(new BigDecimal("12.500")));
        assertThrows(IllegalArgumentException.class, () -> asociacion(new BigDecimal("-1")));
        assertThrows(IllegalArgumentException.class, () -> asociacion(new BigDecimal("1000000.00")));
        assertDoesNotThrow(() -> contexto("x".repeat(150), 1));
        assertThrows(IllegalArgumentException.class, () -> contexto("x".repeat(151), 1));
        assertThrows(IllegalArgumentException.class, () -> contexto("Caso", 0));
    }

    @Test void lasColeccionesRecibidasQuedanAisladas() {
        var respuestas = new ArrayList<>(List.of(new RespuestaReferencia(pregunta, UUID.randomUUID())));
        var intento = new IntentoEvaluacion(UUID.randomUUID(), UUID.randomUUID(), caso, EstadoIntento.FINALIZADO, respuestas);
        respuestas.clear();
        assertEquals(1, intento.respuestas().size());
        var asociaciones = new HashSet<>(Set.of(asociacion(BigDecimal.ONE)));
        var r = new Rubrica(rubrica, caso, "R", null, null, null, null, asociaciones);
        asociaciones.clear();
        assertEquals(1, r.preguntas().size());
        assertThrows(UnsupportedOperationException.class, () -> r.preguntas().clear());
    }

    @Test void estructuraIncompletaNoSeRellenaYUnaRespuestaSinOpcionesSeRechaza() {
        UUID id = UUID.randomUUID();
        var intentos = mock(ConsultaIntentoEvaluacionPort.class);
        var emisor = mock(SolicitudCapacidadContextoAcademicoPort.class);
        var contextos = mock(ConsultaContextoAcademicoPort.class);
        when(intentos.findById(id)).thenReturn(Optional.of(new IntentoEvaluacion(id, UUID.randomUUID(), caso,
                EstadoIntento.FINALIZADO, List.of(new RespuestaReferencia(pregunta, UUID.randomUUID())))));
        when(emisor.solicitarCapacidad(id, caso)).thenReturn("fixture");
        var sinOpciones = new Pregunta(pregunta, escenario, "E", null, "Borrador", true, null, null, null, List.of());
        when(contextos.findByCasoId(caso, "fixture")).thenReturn(Optional.of(new ContextoAcademico(caso, UUID.randomUUID(), "Caso", null, null, 60,
                "Borrador", true, null, null, null, List.of(new Escenario(escenario, caso, null, null, null, 1, true, List.of(sinOpciones))), null)));
        var error = assertThrows(PreparacionContextoException.class,
                () -> new PrepararContextoEvaluacionService(intentos, emisor, contextos).preparar(id));
        assertEquals(Codigo.REFERENCIAS_INCONSISTENTES, error.codigo());
    }

    private RubricaPregunta asociacion(BigDecimal valor) {
        return new RubricaPregunta(UUID.randomUUID(), rubrica, pregunta, valor, null);
    }

    private ContextoAcademico contexto(String nombre, int tiempo) {
        return new ContextoAcademico(caso, UUID.randomUUID(), nombre, null, null, tiempo, "Borrador", true, null, null, null, List.of(), null);
    }
}
