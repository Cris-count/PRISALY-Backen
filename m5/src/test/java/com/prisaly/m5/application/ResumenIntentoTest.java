package com.prisaly.m5.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import com.prisaly.m5.domain.intento.EstadoIntento;
import com.prisaly.m5.domain.intento.Intento;
import com.prisaly.m5.domain.intento.MotivoFinalizacion;
import com.prisaly.m5.domain.respuesta.ReferenciaPregunta;
import com.prisaly.m5.domain.respuesta.Respuesta;
import com.prisaly.m5.domain.respuesta.ValorRespuesta;

@DisplayName("Tipos de aplicación: ReferenciaIntento y ResumenIntento")
class ResumenIntentoTest {

    private static final ReferenciaIntento REF = new ReferenciaIntento("I-1");
    private static final ReferenciaPregunta P1 = new ReferenciaPregunta("P-1");
    private static final ValorRespuesta A = new ValorRespuesta("Opción A");

    @ParameterizedTest(name = "referencia inválida: \"{0}\"")
    @NullAndEmptySource
    @ValueSource(strings = {"  "})
    void rechazaReferenciaVacia(String valor) {
        assertThrows(DatoAplicacionInvalidoException.class, () -> new ReferenciaIntento(valor));
    }

    @Test
    @DisplayName("las referencias se comparan por valor")
    void referenciasSeComparanPorValor() {
        assertEquals(new ReferenciaIntento("I-1"), REF);
    }

    @Test
    @DisplayName("el resumen refleja estado, motivo y respuestas del intento")
    void resumenReflejaElIntento() {
        Intento intento = Intento.iniciar();
        intento.registrarRespuesta(P1, A);
        intento.finalizar(MotivoFinalizacion.TIEMPO_VENCIDO);

        ResumenIntento resumen = ResumenIntento.de(REF, intento);

        assertEquals(REF, resumen.referencia());
        assertEquals(EstadoIntento.FINALIZADO, resumen.estado());
        assertEquals(Optional.of(MotivoFinalizacion.TIEMPO_VENCIDO), resumen.motivoFinalizacion());
        assertEquals(List.of(new Respuesta(P1, A)), resumen.respuestas());
    }

    @Test
    @DisplayName("el resumen es una instantánea: no cambia si el intento cambia después")
    void resumenEsUnaInstantanea() {
        Intento intento = Intento.iniciar();
        ResumenIntento resumen = ResumenIntento.de(REF, intento);

        intento.registrarRespuesta(P1, A);
        intento.finalizar(MotivoFinalizacion.ENTREGA_ESTUDIANTE);

        assertEquals(EstadoIntento.EN_CURSO, resumen.estado());
        assertEquals(List.of(), resumen.respuestas());
        assertThrows(UnsupportedOperationException.class, () -> resumen.respuestas().add(new Respuesta(P1, A)));
    }

    @Test
    @DisplayName("el resumen exige referencia e intento")
    void resumenExigeDatos() {
        assertThrows(DatoAplicacionInvalidoException.class, () -> ResumenIntento.de(REF, null));
        assertThrows(DatoAplicacionInvalidoException.class, () -> ResumenIntento.de(null, Intento.iniciar()));
    }

    @Test
    @DisplayName("las respuestas salen ordenadas por pregunta, no por orden de registro")
    void respuestasOrdenadasPorPregunta() {
        Intento intento = Intento.iniciar();
        intento.registrarRespuesta(new ReferenciaPregunta("P-2"), A);
        intento.registrarRespuesta(P1, A);

        assertEquals(List.of(new Respuesta(P1, A), new Respuesta(new ReferenciaPregunta("P-2"), A)),
                ResumenIntento.de(REF, intento).respuestas());
    }

    @Test
    @DisplayName("el orden de uuid coincide con PostgreSQL (bytes sin signo), no con UUID.compareTo")
    void ordenUuidComoPostgreSql() {
        // 8… es mayor que 1… como bytes sin signo; UUID.compareTo (con signo) lo pondría primero.
        ReferenciaPregunta alta = new ReferenciaPregunta("80000000-0000-0000-0000-000000000000");
        ReferenciaPregunta baja = new ReferenciaPregunta("10000000-0000-0000-0000-000000000000");
        Intento intento = Intento.iniciar();
        intento.registrarRespuesta(alta, A);
        intento.registrarRespuesta(baja, A);

        assertEquals(List.of(baja, alta), ResumenIntento.de(REF, intento).respuestas().stream()
                .map(Respuesta::pregunta).toList());
        assertTrue(UUID.fromString(alta.valor()).compareTo(UUID.fromString(baja.valor())) < 0,
                "precondición: UUID.compareTo ordena al revés");
    }

    @Test
    @DisplayName("el orden no depende de mayúsculas en una referencia uuid")
    void ordenIndependienteDeMayusculas() {
        ReferenciaPregunta mayusculas = new ReferenciaPregunta("BBBBBBBB-0000-0000-0000-000000000000");
        ReferenciaPregunta minusculas = new ReferenciaPregunta("aaaaaaaa-0000-0000-0000-000000000000");
        Intento intento = Intento.iniciar();
        intento.registrarRespuesta(mayusculas, A);
        intento.registrarRespuesta(minusculas, A);

        assertEquals(List.of(minusculas, mayusculas), ResumenIntento.de(REF, intento).respuestas().stream()
                .map(Respuesta::pregunta).toList());
    }
}
