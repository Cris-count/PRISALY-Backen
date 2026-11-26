package com.prisaly.m3;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.prisaly.m3.domain.builder.CasoBuilder;
import com.prisaly.m3.domain.model.Caso;
import com.prisaly.m3.domain.model.CasoEstado;
import com.prisaly.m3.domain.model.OpcionRespuesta;
import com.prisaly.m3.domain.model.Pregunta;
import com.prisaly.m3.domain.rule.CasoDomainException;
import com.prisaly.m3.domain.rule.CasoPublication;
import com.prisaly.m3.domain.rule.CasoValidation;
import java.util.List;
import org.junit.jupiter.api.Test;

/** DC-111: questions become Lista only through a successful publication; an invalid question changes nothing. */
class CasoPublicacionListaDc111Test {

    private static CasoBuilder base() {
        return new CasoBuilder().withId("case").withTitulo("Caso").withDocenteId("docente")
                .withTiempoEstimadoSegundos(300).withRubricaId("rubrica").addCriterio("c", "Criterio", 10)
                .addEscenario("escenario", "Descripcion")
                .addPreguntaSeleccionUnica("p1", "Uno", List.of(new OpcionRespuesta("a", "A", true), new OpcionRespuesta("b", "B", false)))
                .and();
    }

    private static List<String> estados(Caso caso) {
        return caso.escenarios().stream().flatMap(e -> e.preguntas().stream()).map(Pregunta::estadoEdicion).toList();
    }

    @Test
    void elBorradorConservaLasPreguntasEnBorrador() {
        Caso borrador = base().buildDraft();
        assertEquals(CasoEstado.BORRADOR, borrador.estado());
        assertEquals(List.of("Borrador"), estados(borrador));
    }

    @Test
    void publicarPasaTodasLasPreguntasValidasAListaSinTocarNadaMas() {
        Caso borrador = base().addEscenario("e2", "Dos")
                .addPreguntaSeleccionUnica("p2", "Dos", List.of(new OpcionRespuesta("c", "C", true))).and().buildDraft();
        Caso publicado = CasoPublication.publish(borrador);
        assertEquals(CasoEstado.PUBLICADO, publicado.estado());
        assertEquals(List.of("Lista", "Lista"), estados(publicado));
        assertSame(borrador.rubrica(), publicado.rubrica(), "rubric unchanged");
        for (int i = 0; i < borrador.escenarios().size(); i++) {
            var antes = borrador.escenarios().get(i);
            var despues = publicado.escenarios().get(i);
            assertEquals(antes.id(), despues.id());
            assertEquals(antes.activo(), despues.activo());
            for (int j = 0; j < antes.preguntas().size(); j++) {
                Pregunta a = antes.preguntas().get(j), d = despues.preguntas().get(j);
                assertEquals(a.id(), d.id());
                assertEquals(a.opciones(), d.opciones(), "options and correctness unchanged");
                assertEquals(a.visible(), d.visible());
                assertEquals(a.orden(), d.orden());
                assertEquals(a.fechaCreacion(), d.fechaCreacion());
                assertEquals(a.fechaActualizacion(), d.fechaActualizacion());
            }
        }
    }

    @Test
    void unaPreguntaIncompletaRechazaLaPublicacionYNoHayTransicionParcial() {
        // The builder already refuses an incomplete question, so the invalid draft is assembled from the records.
        Caso valido = base().buildDraft();
        var invalida = new com.prisaly.m3.domain.model.Escenario("e2", "Dos",
                List.of(new Pregunta("p2", "Sin clave", List.of(new OpcionRespuesta("c", "C", false)))));
        var escenarios = new java.util.ArrayList<>(valido.escenarios());
        escenarios.add(invalida);
        Caso borrador = new Caso(valido.id(), valido.titulo(), valido.docenteId(), valido.tiempoEstimadoSegundos(),
                valido.estado(), escenarios, valido.rubrica(), valido.contenidoEvaluableBloqueado(), valido.historia(),
                valido.desarrolloSituacional(), valido.activo(), valido.fechaCreacion(), valido.fechaActualizacion(),
                valido.fechaBloqueoAcademico());
        CasoDomainException e = assertThrows(CasoDomainException.class, () -> CasoPublication.publish(borrador));
        assertEquals(CasoValidation.CODE_INCONSISTENT_OPTIONS, e.code());
        assertEquals(List.of("Borrador", "Borrador"), estados(borrador), "the valid question stays Borrador too");
        assertEquals(CasoEstado.BORRADOR, borrador.estado());
    }
}
