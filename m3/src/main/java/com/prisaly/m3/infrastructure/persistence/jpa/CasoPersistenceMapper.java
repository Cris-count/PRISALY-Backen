package com.prisaly.m3.infrastructure.persistence.jpa;

import com.prisaly.m3.domain.model.Caso;
import com.prisaly.m3.domain.model.CasoEstado;
import com.prisaly.m3.domain.model.Escenario;
import com.prisaly.m3.domain.model.OpcionRespuesta;
import com.prisaly.m3.domain.model.Pregunta;
import com.prisaly.m3.domain.model.Rubrica;
import com.prisaly.m3.domain.model.RubricaPregunta;
import com.prisaly.m3.domain.rule.RubricaStructureValidation;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.stream.Collectors;

/** M3-INC-10A: explicit, lossless mapping of the representable SQL subset. */
final class CasoPersistenceMapper {
    private CasoPersistenceMapper() { }

    static UUID uuid(String value) {
        return RubricaStructureValidation.requireUuid(value);
    }

    static void validateInsert(Caso caso) {
        Objects.requireNonNull(caso, "caso");
        uuid(caso.id());
        uuid(caso.docenteId());
        // Never manufacture or import an academic lock through INSERT.
        if (caso.contenidoEvaluableBloqueado() || caso.fechaBloqueoAcademico() != null) {
            throw new IllegalArgumentException("Academic locking is established only by official session triggers");
        }
        exactTimestamp(caso.fechaCreacion());
        exactTimestamp(caso.fechaActualizacion());
        for (Escenario escenario : caso.escenarios()) {
            uuid(escenario.id());
            if (escenario.descripcion() != null) {
                throw new IllegalArgumentException("Legacy Escenario.descripcion has no approved SQL mapping");
            }
            Objects.requireNonNull(escenario.orden(), "Escenario.orden must be supplied explicitly");
            for (Pregunta pregunta : escenario.preguntas()) {
                uuid(pregunta.id());
                Objects.requireNonNull(pregunta.orden(), "Pregunta.orden must be supplied explicitly");
                exactTimestamp(pregunta.fechaCreacion());
                exactTimestamp(pregunta.fechaActualizacion());
                for (OpcionRespuesta opcion : pregunta.opciones()) {
                    uuid(opcion.id());
                    Objects.requireNonNull(opcion.orden(), "OpcionRespuesta.orden must be supplied explicitly");
                }
            }
        }
        Rubrica rubrica = caso.rubrica();
        if (rubrica != null) {
            if (!rubrica.hasExplicitStructure() || !rubrica.criterios().isEmpty()) {
                throw new IllegalArgumentException("Legacy CriterioRubrica has no approved SQL equivalence");
            }
            RubricaStructureValidation.assertBelongsToCase(caso.id(), caso.escenarios(), rubrica);
            exactTimestamp(rubrica.fechaCreacion());
        }
    }

    private static void exactTimestamp(Instant value) {
        if (value != null && value.getNano() % 1_000 != 0) {
            throw new IllegalArgumentException("TIMESTAMPTZ requires microsecond precision; implicit rounding is not allowed");
        }
    }

    static String state(CasoEstado state) {
        return switch (state) {
            case BORRADOR -> "Borrador";
            case PUBLICADO -> "Publicado";
            case ARCHIVADO -> "Archivado";
        };
    }

    static Caso toDomain(CasoJpaAggregateSnapshot snapshot) {
        var row = snapshot.caso();
        List<Escenario> escenarios = snapshot.escenarios().stream().map(e -> new Escenario(
                e.idEscenario().toString(), e.titulo(), e.contexto(), e.instrucciones(), e.orden(), e.activo(),
                snapshot.preguntas().stream().filter(p -> p.idEscenario().equals(e.idEscenario()))
                        .map(p -> new Pregunta(p.idPregunta().toString(), p.enunciado(),
                                snapshot.opciones().stream().filter(o -> o.idPregunta().equals(p.idPregunta()))
                                        .map(o -> new OpcionRespuesta(o.idOpcion().toString(), o.texto(),
                                                o.esCorrecta(), o.explicacion(), o.orden())).toList(),
                                p.textoAyuda(), p.estadoEdicion(), p.visible(), p.orden(),
                                p.fechaCreacion(), p.fechaActualizacion())).toList())).toList();
        var r = snapshot.rubrica();
        Rubrica rubrica = r == null ? null : new Rubrica(r.idRubrica().toString(), List.of(),
                r.idCaso().toString(), r.nombre(), r.descripcion(), r.puntajeTotal(),
                r.definicionEvaluacion(), r.fechaCreacion(), snapshot.rubricaPreguntas().stream()
                        .map(a -> new RubricaPregunta(a.idRubricaPregunta().toString(), a.idRubrica().toString(),
                                a.idPregunta().toString(), a.valorPregunta(), a.criterio()))
                        .collect(Collectors.toUnmodifiableSet()));
        CasoEstado estado = switch (row.estado()) {
            case "Borrador" -> CasoEstado.BORRADOR;
            case "Publicado" -> CasoEstado.PUBLICADO;
            case "Archivado" -> CasoEstado.ARCHIVADO;
            default -> throw new IllegalStateException("Unsupported stored case state: " + row.estado());
        };
        return new Caso(row.idCaso().toString(), row.nombre(), row.idDocente().toString(),
                row.tiempoEstimadoSegundos(), estado, escenarios, rubrica, row.fechaBloqueoAcademico() != null,
                row.historia(), row.desarrolloSituacional(), row.activo(), row.fechaCreacion(),
                row.fechaActualizacion(), row.fechaBloqueoAcademico());
    }
}
