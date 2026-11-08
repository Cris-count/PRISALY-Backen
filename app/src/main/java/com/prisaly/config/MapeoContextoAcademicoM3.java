package com.prisaly.config;

import com.prisaly.m3.api.ContextoAcademicoPersistido;
import com.prisaly.m6.application.integration.ContextoAcademico;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * Pure structural translation of M3's protected snapshot into M6's consumer record.
 *
 * <p>Not a Spring component, not an adapter and not a provider: it grants no read authorization,
 * calls no port and keeps no state. Only an APP adapter that has already obtained the snapshot
 * through {@code ContextoAcademicoConsultaPort} with a verified capability may use it.
 *
 * <p>Fields are copied by name. Both records declare three {@code Instant} values in a different
 * order (M3: bloqueo, creacion, actualizacion; M6: creacion, actualizacion, bloqueo), so a
 * positional copy would compile and silently swap them. Nothing is defaulted, rounded or
 * inferred: M6 constructors reject values outside its SQL-shaped contract, and those failures
 * propagate as technical errors, never as absence or denial. Correct answers and explanations
 * are preserved because this snapshot exists only inside the protected evaluation flow.
 */
public final class MapeoContextoAcademicoM3 {

    private MapeoContextoAcademicoM3() {
    }

    public static ContextoAcademico aContextoAcademico(ContextoAcademicoPersistido origen) {
        Objects.requireNonNull(origen, "origen");
        return new ContextoAcademico(
                origen.idCaso(),
                origen.idDocente(),
                origen.nombre(),
                origen.historia(),
                origen.desarrolloSituacional(),
                origen.tiempoEstimadoSegundos(),
                origen.estado(),
                origen.activo(),
                origen.fechaCreacion(),
                origen.fechaActualizacion(),
                origen.fechaBloqueoAcademico(),
                origen.escenarios().stream().map(MapeoContextoAcademicoM3::escenario).toList(),
                origen.rubrica() == null ? null : rubrica(origen.rubrica()));
    }

    private static ContextoAcademico.Escenario escenario(ContextoAcademicoPersistido.Escenario e) {
        return new ContextoAcademico.Escenario(e.idEscenario(), e.idCaso(), e.titulo(), e.contexto(),
                e.instrucciones(), e.orden(), e.activo(),
                e.preguntas().stream().map(MapeoContextoAcademicoM3::pregunta).toList());
    }

    private static ContextoAcademico.Pregunta pregunta(ContextoAcademicoPersistido.Pregunta p) {
        return new ContextoAcademico.Pregunta(p.idPregunta(), p.idEscenario(), p.enunciado(),
                p.textoAyuda(), p.estadoEdicion(), p.visible(), p.orden(), p.fechaCreacion(),
                p.fechaActualizacion(),
                p.opciones().stream().map(MapeoContextoAcademicoM3::opcion).toList());
    }

    private static ContextoAcademico.Opcion opcion(ContextoAcademicoPersistido.Opcion o) {
        return new ContextoAcademico.Opcion(o.idOpcion(), o.idPregunta(), o.texto(), o.esCorrecta(),
                o.explicacion(), o.orden());
    }

    private static ContextoAcademico.Rubrica rubrica(ContextoAcademicoPersistido.Rubrica r) {
        return new ContextoAcademico.Rubrica(r.idRubrica(), r.idCaso(), r.nombre(), r.descripcion(),
                r.puntajeTotal(), r.definicionEvaluacion(), r.fechaCreacion(),
                r.preguntas().stream().map(MapeoContextoAcademicoM3::asociacion).collect(Collectors.toSet()));
    }

    private static ContextoAcademico.RubricaPregunta asociacion(ContextoAcademicoPersistido.RubricaPregunta rp) {
        return new ContextoAcademico.RubricaPregunta(rp.idRubricaPregunta(), rp.idRubrica(),
                rp.idPregunta(), rp.valorPregunta(), rp.criterio());
    }
}
