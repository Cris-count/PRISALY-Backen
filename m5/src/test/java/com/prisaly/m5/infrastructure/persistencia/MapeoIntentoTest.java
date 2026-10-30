package com.prisaly.m5.infrastructure.persistencia;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.prisaly.m5.application.ReferenciaIntento;
import com.prisaly.m5.domain.intento.EstadoIntento;
import com.prisaly.m5.domain.intento.Intento;
import com.prisaly.m5.domain.intento.MotivoFinalizacion;
import com.prisaly.m5.domain.respuesta.ReferenciaPregunta;
import com.prisaly.m5.domain.respuesta.Respuesta;
import com.prisaly.m5.domain.respuesta.ValorRespuesta;
import com.prisaly.m5.infrastructure.persistencia.MapeoIntento.CambioRespuesta;
import com.prisaly.m5.infrastructure.persistencia.MapeoIntento.FilaIntento;
import com.prisaly.m5.infrastructure.persistencia.MapeoIntento.FilaRespuesta;

@DisplayName("MapeoIntento: dominio ↔ filas de BD-005.2")
class MapeoIntentoTest {

    private static final UUID P1 = UUID.randomUUID();
    private static final UUID P2 = UUID.randomUUID();
    private static final UUID O1 = UUID.randomUUID();
    private static final UUID O2 = UUID.randomUUID();

    private static Respuesta respuesta(UUID pregunta, UUID opcion) {
        return new Respuesta(new ReferenciaPregunta(pregunta.toString()), new ValorRespuesta(opcion.toString()));
    }

    @Test
    @DisplayName("traduce los estados al catálogo aprobado EnCurso / Finalizado")
    void traduceEstados() {
        assertEquals("EnCurso", MapeoIntento.estadoAColumna(EstadoIntento.EN_CURSO));
        assertEquals("Finalizado", MapeoIntento.estadoAColumna(EstadoIntento.FINALIZADO));
    }

    @Test
    @DisplayName("solo las referencias con formato uuid pueden existir en la base")
    void idDeReferencia() {
        UUID id = UUID.randomUUID();
        assertEquals(Optional.of(id), MapeoIntento.idDe(new ReferenciaIntento(id.toString())));
        assertEquals(Optional.empty(), MapeoIntento.idDe(new ReferenciaIntento("I-1")));
    }

    @Test
    @DisplayName("reconstruye un intento EnCurso con sus respuestas")
    void reconstruyeEnCurso() {
        Intento intento = MapeoIntento.reconstruir(new FilaIntento("EnCurso", null),
                List.of(new FilaRespuesta(P1, O1), new FilaRespuesta(P2, O2)));

        assertEquals(EstadoIntento.EN_CURSO, intento.estado());
        assertEquals(List.of(respuesta(P1, O1), respuesta(P2, O2)), intento.respuestas());
    }

    @Test
    @DisplayName("reconstruye un intento Finalizado que conserva motivo y respuestas y no admite cambios")
    void reconstruyeFinalizado() {
        Intento intento = MapeoIntento.reconstruir(new FilaIntento("Finalizado", "TIEMPO_VENCIDO"),
                List.of(new FilaRespuesta(P1, O1)));

        assertEquals(EstadoIntento.FINALIZADO, intento.estado());
        assertEquals(Optional.of(MotivoFinalizacion.TIEMPO_VENCIDO), intento.motivoFinalizacion());
        assertEquals(List.of(respuesta(P1, O1)), intento.respuestas());
        assertThrows(RuntimeException.class, () -> intento.registrarRespuesta(
                new ReferenciaPregunta(P2.toString()), new ValorRespuesta(O2.toString())));
    }

    @Test
    @DisplayName("rechaza filas que violan las reglas del dominio")
    void rechazaFilasInconsistentes() {
        assertThrows(PersistenciaIntentoException.class,
                () -> MapeoIntento.reconstruir(new FilaIntento("Finalizado", null), List.of()));
        assertThrows(PersistenciaIntentoException.class,
                () -> MapeoIntento.reconstruir(new FilaIntento("Finalizado", "OTRO"), List.of()));
        assertThrows(PersistenciaIntentoException.class,
                () -> MapeoIntento.reconstruir(new FilaIntento("EnCurso", "TIEMPO_VENCIDO"), List.of()));
        assertThrows(PersistenciaIntentoException.class,
                () -> MapeoIntento.reconstruir(new FilaIntento("Vencido", null), List.of()));
    }

    @Test
    @DisplayName("calcula inserciones y reemplazos, y omite respuestas sin cambios")
    void calculaCambios() {
        Intento intento = Intento.iniciar();
        intento.registrarRespuesta(new ReferenciaPregunta(P1.toString()), new ValorRespuesta(O2.toString()));
        intento.registrarRespuesta(new ReferenciaPregunta(P2.toString()), new ValorRespuesta(O2.toString()));

        assertEquals(List.of(new CambioRespuesta(P1, O2, false), new CambioRespuesta(P2, O2, true)),
                MapeoIntento.cambios(Map.of(P1, O1), intento));
        assertEquals(List.of(), MapeoIntento.cambios(Map.of(P1, O2, P2, O2), intento));
    }

    @Test
    @DisplayName("no guarda si la base tiene respuestas que el agregado no conoce")
    void rechazaRespuestasDesconocidas() {
        assertThrows(PersistenciaIntentoException.class,
                () -> MapeoIntento.cambios(Map.of(P1, O1), Intento.iniciar()));
    }

    @Test
    @DisplayName("exige que pregunta y opción sean identificadores uuid")
    void exigeUuid() {
        Intento intento = Intento.iniciar();
        intento.registrarRespuesta(new ReferenciaPregunta("P-1"), new ValorRespuesta(O1.toString()));

        assertThrows(PersistenciaIntentoException.class, () -> MapeoIntento.cambios(Map.of(), intento));
    }
}
