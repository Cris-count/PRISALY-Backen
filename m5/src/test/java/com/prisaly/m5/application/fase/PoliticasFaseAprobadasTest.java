package com.prisaly.m5.application.fase;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.prisaly.m5.application.LimiteTemporalVencidoException;
import com.prisaly.m5.application.ParticipacionAplicacionException;
import com.prisaly.m5.application.ReferenciaIntento;
import com.prisaly.m5.domain.intento.EstadoIntento;
import com.prisaly.m5.presentation.TraductorErroresM5;

import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** DC-BE-SIM-B2B-B3-32: una comprobación identificable por decisión aprobada y su contrato de error HTTP. */
@DisplayName("Políticas aprobadas D-SIM-2, 3, 4 y 10")
class PoliticasFaseAprobadasTest {

    final PoliticasFaseAprobadas politicas = new PoliticasFaseAprobadas();
    final ReferenciaIntento intento = new ReferenciaIntento(UUID.randomUUID().toString());

    @Test @DisplayName("D-SIM-2: preguntas ocultas sin cuestionario, en curso o finalizado → 409 CUESTIONARIO_NO_INICIADO")
    void dSim2() {
        for (EstadoIntento estado : EstadoIntento.values()) {
            var e = assertThrows(CuestionarioNoIniciadoException.class, () -> politicas.preguntasSinCuestionario(intento, estado));
            assertCodigo(e, 409, "CUESTIONARIO_NO_INICIADO");
        }
    }

    @Test @DisplayName("D-SIM-3: entrada al cuestionario tras el límite → 409 LIMITE_TEMPORAL_VENCIDO")
    void dSim3() {
        var e = assertThrows(LimiteTemporalVencidoException.class, () -> politicas.entradaAlCuestionarioTrasElLimite(intento));
        assertEquals(PoliticasFaseAprobadas.MENSAJE_LIMITE, e.getMessage());
        assertCodigo(e, 409, "LIMITE_TEMPORAL_VENCIDO");
    }

    @Test @DisplayName("D-SIM-4: finalizar desde exploración está permitido")
    void dSim4() {
        assertDoesNotThrow(() -> politicas.finalizacionDesdeExploracion(intento));
    }

    @Test @DisplayName("D-SIM-10: narrativa de un intento finalizado → 409 EXPLORACION_CERRADA")
    void dSim10() {
        var e = assertThrows(ExploracionCerradaException.class, () -> politicas.narrativaDeIntentoFinalizado(intento));
        assertCodigo(e, 409, "EXPLORACION_CERRADA");
    }

    @Test @DisplayName("el resto de errores de fase tienen código tipado (409) en lugar de un 500 genérico")
    void codigosDeFase() {
        Map<ParticipacionAplicacionException, String> codigos = Map.of(
                new CuestionarioNoIniciadoException(), "CUESTIONARIO_NO_INICIADO",
                new SinFasesException(), "INTENTO_SIN_FASES",
                new NarrativaNoDisponibleException(), "NARRATIVA_NO_DISPONIBLE",
                new FasesNoDisponiblesException(), "FASES_NO_DISPONIBLES");
        codigos.forEach((error, codigo) -> assertCodigo(error, 409, codigo));
    }

    static void assertCodigo(RuntimeException error, int estado, String codigo) {
        var traducido = TraductorErroresM5.traducir(error).orElseThrow();
        assertEquals(estado, traducido.estado());
        assertEquals(codigo, traducido.cuerpo().code());
        assertEquals(error.getMessage(), traducido.cuerpo().message());
    }
}
