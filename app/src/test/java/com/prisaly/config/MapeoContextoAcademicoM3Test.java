package com.prisaly.config;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.prisaly.m3.api.ContextoAcademicoPersistido;
import com.prisaly.m3.api.ContextoAcademicoPersistido.*;
import com.prisaly.m6.application.integration.ContextoAcademico;
import com.prisaly.m6.application.integration.IntentoEvaluacion;
import com.prisaly.m6.application.integration.IntentoEvaluacion.RespuestaReferencia;
import com.prisaly.m6.application.port.ConsultaContextoAcademicoPort;
import com.prisaly.m6.application.port.ConsultaIntentoEvaluacionPort;
import com.prisaly.m6.application.port.SolicitudCapacidadContextoAcademicoPort;
import com.prisaly.m6.application.service.PrepararContextoEvaluacionService;
import java.lang.reflect.Modifier;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class MapeoContextoAcademicoM3Test {
    private static final Instant BLOQUEO = Instant.parse("2026-03-03T03:03:03.333333Z");
    private static final Instant CREACION = Instant.parse("2026-01-01T01:01:01.111111Z");
    private static final Instant ACTUALIZACION = Instant.parse("2026-02-02T02:02:02.222222Z");
    private final UUID caso = UUID.randomUUID(), docente = UUID.randomUUID(), escenario = UUID.randomUUID();
    private final UUID pregunta1 = UUID.randomUUID(), pregunta2 = UUID.randomUUID();
    private final UUID opcionA = UUID.randomUUID(), opcionB = UUID.randomUUID(), rubrica = UUID.randomUUID();
    private final UUID asociacion = UUID.randomUUID();

    @Test
    void copiaPorNombreSinIntercambiarLasTresFechasDelCaso() {
        var m6 = MapeoContextoAcademicoM3.aContextoAcademico(persistido("Publicado", 2, "Nombre", new BigDecimal("2.50")));
        assertEquals(CREACION, m6.fechaCreacion());
        assertEquals(ACTUALIZACION, m6.fechaActualizacion());
        assertEquals(BLOQUEO, m6.fechaBloqueoAcademico());
        assertEquals(caso, m6.idCaso());
        assertEquals(docente, m6.idDocente());
        assertEquals("Historia", m6.historia());
        assertEquals("Desarrollo", m6.desarrolloSituacional());
        assertEquals(900, m6.tiempoEstimadoSegundos());
        assertEquals("Publicado", m6.estado());
        assertTrue(m6.activo());
    }

    @Test
    void conservaJerarquiaOrdenNulosYDatosProtegidosSinInventarValores() {
        var m6 = MapeoContextoAcademicoM3.aContextoAcademico(persistido("Borrador", 2, "Nombre", new BigDecimal("2.50")));
        var e = m6.escenarios().getFirst();
        assertEquals(new ContextoAcademico.Escenario(escenario, caso, null, "Contexto", null, 1, false, e.preguntas()), e);
        assertEquals(List.of(pregunta1, pregunta2), e.preguntas().stream().map(ContextoAcademico.Pregunta::idPregunta).toList());
        var p = e.preguntas().getFirst();
        assertEquals(escenario, p.idEscenario());
        assertEquals("Lista", p.estadoEdicion());
        assertEquals(CREACION, p.fechaCreacion());
        assertNull(p.fechaActualizacion());
        assertNull(p.textoAyuda());
        assertEquals(Integer.valueOf(2), e.preguntas().get(1).orden());
        var opciones = p.opciones();
        assertEquals(List.of(opcionA, opcionB), opciones.stream().map(ContextoAcademico.Opcion::idOpcion).toList());
        // Correctness and explanation remain only inside this protected snapshot.
        assertTrue(opciones.getFirst().esCorrecta());
        assertEquals("Porque si", opciones.getFirst().explicacion());
        assertFalse(opciones.get(1).esCorrecta());
        assertNull(opciones.get(1).explicacion());
        var r = m6.rubrica();
        assertEquals(new ContextoAcademico.Rubrica(rubrica, caso, "Rubrica", null, new BigDecimal("2.50"),
                "{\"criterio\":null}", CREACION, Set.of(new ContextoAcademico.RubricaPregunta(
                        asociacion, rubrica, pregunta1, new BigDecimal("2.50"), "Criterio"))), r);
        assertThrows(UnsupportedOperationException.class, () -> m6.escenarios().clear());
        assertThrows(UnsupportedOperationException.class, () -> p.opciones().clear());
    }

    @Test
    void casoSinRubricaNiEscenariosNoFabricaEstructura() {
        var bare = new ContextoAcademicoPersistido(caso, docente, "Nombre", null, null, 60, "Borrador", false,
                null, CREACION, null, List.of(), null);
        var m6 = MapeoContextoAcademicoM3.aContextoAcademico(bare);
        assertNull(m6.rubrica());
        assertTrue(m6.escenarios().isEmpty());
        assertNull(m6.fechaBloqueoAcademico());
        assertNull(m6.fechaActualizacion());
        assertThrows(NullPointerException.class, () -> MapeoContextoAcademicoM3.aContextoAcademico(null));
    }

    @Test
    void valoresQueM3AdmitePeroM6RechazaSonFallosTecnicosNoAusencia() {
        // Unknown case state, zero order (V002 >= 1), more than 150 code points, NUMERIC(8,2) violations.
        assertThrows(IllegalArgumentException.class,
                () -> MapeoContextoAcademicoM3.aContextoAcademico(persistido("Eliminado", 2, "Nombre", BigDecimal.ONE)));
        assertThrows(IllegalArgumentException.class,
                () -> MapeoContextoAcademicoM3.aContextoAcademico(persistido("Publicado", 0, "Nombre", BigDecimal.ONE)));
        assertThrows(IllegalArgumentException.class,
                () -> MapeoContextoAcademicoM3.aContextoAcademico(persistido("Publicado", 2, "x".repeat(151), BigDecimal.ONE)));
        assertThrows(IllegalArgumentException.class,
                () -> MapeoContextoAcademicoM3.aContextoAcademico(persistido("Publicado", 2, "Nombre", new BigDecimal("1.005"))));
        assertThrows(IllegalArgumentException.class,
                () -> MapeoContextoAcademicoM3.aContextoAcademico(persistido("Publicado", 2, "Nombre", BigDecimal.ZERO)));
        // Code points, not UTF-16 units: 150 emoji are valid.
        assertDoesNotThrow(() -> MapeoContextoAcademicoM3.aContextoAcademico(
                persistido("Publicado", 2, "😀".repeat(150), BigDecimal.ONE)));
    }

    @Test
    void elResultadoEsCompatibleConLaPreparacionProtegidaDeM6() {
        var intentos = mock(ConsultaIntentoEvaluacionPort.class);
        var capacidades = mock(SolicitudCapacidadContextoAcademicoPort.class);
        var contextos = mock(ConsultaContextoAcademicoPort.class);
        UUID intento = UUID.randomUUID();
        when(intentos.findById(intento)).thenReturn(Optional.of(new IntentoEvaluacion(intento, UUID.randomUUID(), caso,
                IntentoEvaluacion.EstadoIntento.FINALIZADO, List.of(new RespuestaReferencia(pregunta1, opcionB)))));
        when(capacidades.solicitarCapacidad(intento, caso)).thenReturn("fixture");
        var mapeado = MapeoContextoAcademicoM3.aContextoAcademico(persistido("Publicado", 2, "Nombre", BigDecimal.ONE));
        when(contextos.findByCasoId(caso, "fixture")).thenReturn(Optional.of(mapeado));
        var preparado = new PrepararContextoEvaluacionService(intentos, capacidades, contextos).preparar(intento);
        assertSame(mapeado, preparado.contextoAcademico());
    }

    @Test
    void noEsComponenteNiInstanciable() {
        assertEquals(0, MapeoContextoAcademicoM3.class.getAnnotations().length);
        assertTrue(Modifier.isFinal(MapeoContextoAcademicoM3.class.getModifiers()));
        for (var constructor : MapeoContextoAcademicoM3.class.getDeclaredConstructors()) {
            assertTrue(Modifier.isPrivate(constructor.getModifiers()));
        }
    }

    private ContextoAcademicoPersistido persistido(String estado, int ordenSegunda, String nombre, BigDecimal valor) {
        var opciones = List.of(new Opcion(opcionA, pregunta1, "A", true, "Porque si", 1),
                new Opcion(opcionB, pregunta1, "B", false, null, 2));
        var preguntas = List.of(
                new Pregunta(pregunta1, escenario, "Enunciado 1", null, "Lista", true, 1, CREACION, null, opciones),
                new Pregunta(pregunta2, escenario, "Enunciado 2", "Ayuda", "Borrador", false, ordenSegunda, CREACION,
                        ACTUALIZACION, List.of()));
        var escenarios = List.of(new Escenario(escenario, caso, null, "Contexto", null, 1, false, preguntas));
        var rubricaM3 = new Rubrica(rubrica, caso, "Rubrica", null, valor, "{\"criterio\":null}", CREACION,
                Set.of(new RubricaPregunta(asociacion, rubrica, pregunta1, valor, "Criterio")));
        return new ContextoAcademicoPersistido(caso, docente, nombre, "Historia", "Desarrollo", 900, estado, true,
                BLOQUEO, CREACION, ACTUALIZACION, escenarios, rubricaM3);
    }
}
