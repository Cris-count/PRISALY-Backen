package com.prisaly.m3.domain.rule;

import com.prisaly.m3.domain.model.Caso;
import com.prisaly.m3.domain.model.CasoEstado;
import com.prisaly.m3.domain.model.CriterioRubrica;
import com.prisaly.m3.domain.model.Escenario;
import com.prisaly.m3.domain.model.OpcionRespuesta;
import com.prisaly.m3.domain.model.Pregunta;
import com.prisaly.m3.domain.model.Rubrica;
import com.prisaly.m3.domain.model.RubricaPregunta;
import java.util.HashMap;
import java.util.Map;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;
import java.util.UUID;

/**
 * F3-03: after the first session is scheduled, evaluable content is locked.
 * Later edits require an independent draft copy.
 */
public final class ContenidoEvaluableLock {

    public static final String CODE_EVALUABLE_CONTENT_LOCKED = "EVALUABLE_CONTENT_LOCKED";
    public static final String CODE_COPY_CASE_ID_EQUALS_SOURCE = "COPY_CASE_ID_EQUALS_SOURCE";
    public static final String CODE_COPY_METADATA_POLICY_PENDING = "COPY_METADATA_POLICY_PENDING";

    private ContenidoEvaluableLock() {
    }

    public static Caso lockAfterFirstSessionScheduled(Caso caso) {
        CasoValidation.assertNotArchived(caso);
        if (caso.contenidoEvaluableBloqueado()) {
            return caso;
        }
        return new Caso(
                caso.id(),
                caso.titulo(),
                caso.docenteId(),
                caso.tiempoEstimadoSegundos(),
                caso.estado(),
                caso.escenarios(),
                caso.rubrica(),
                true,
                caso.historia(),
                caso.desarrolloSituacional(),
                caso.activo(),
                caso.fechaCreacion(),
                caso.fechaActualizacion(),
                caso.fechaBloqueoAcademico());
    }

    public static void assertEditable(Caso caso) {
        if (caso.contenidoEvaluableBloqueado()) {
            throw new CasoDomainException(
                    CODE_EVALUABLE_CONTENT_LOCKED,
                    "Evaluable content of case " + caso.id()
                            + " is locked after the first scheduled session");
        }
        CasoValidation.assertNotArchived(caso);
    }

    public static Caso createIndependentCopy(Caso source, String newId) {
        if (source.id().equals(newId)
                || (source.rubrica() != null && source.rubrica().hasExplicitStructure()
                    && source.id().equalsIgnoreCase(newId))) {
            throw new CasoDomainException(
                    CODE_COPY_CASE_ID_EQUALS_SOURCE,
                    "Independent copy must have a different case id");
        }
        CasoValidation.assertNotArchived(source);
        // M3-INC-09B: copying dates or assigning new ones is not an approved policy.
        // Preserve the existing transient-copy behavior; fail explicitly for dated sources.
        if (source.fechaCreacion() != null || source.fechaActualizacion() != null
                || (source.rubrica() != null && source.rubrica().fechaCreacion() != null)) {
            throw new CasoDomainException(CODE_COPY_METADATA_POLICY_PENDING,
                    "Copy creation/update metadata requires an approved policy");
        }
        Map<String, String> questionIds = new HashMap<>();
        List<Escenario> escenarios = source.escenarios().stream().map(escenario -> new Escenario(
                        UUID.randomUUID().toString(),
                        escenario.descripcion(),
                        escenario.preguntas().stream().map(pregunta -> new Pregunta(
                                copyQuestionId(pregunta.id(), questionIds),
                                pregunta.enunciado(),
                                pregunta.opciones().stream().map(opcion -> new OpcionRespuesta(
                                        UUID.randomUUID().toString(),
                                        opcion.texto(),
                                        opcion.esCorrecta(),
                                        opcion.explicacion(),
                                        opcion.orden())).toList(),
                                pregunta.textoAyuda(),
                                pregunta.estadoEdicion(),
                                pregunta.visible(),
                                pregunta.orden(),
                                pregunta.fechaCreacion(),
                                pregunta.fechaActualizacion())).toList(),
                        escenario.titulo(),
                        escenario.contexto(),
                        escenario.instrucciones(),
                        escenario.orden(),
                        escenario.activo())).toList();
        return new Caso(
                newId,
                source.titulo(),
                source.docenteId(),
                source.tiempoEstimadoSegundos(),
                CasoEstado.BORRADOR,
                escenarios,
                copyRubrica(source.rubrica(), newId, questionIds),
                false,
                source.historia(),
                source.desarrolloSituacional(),
                source.activo(),
                null,
                null,
                null);
    }

    private static String copyQuestionId(String originalId, Map<String, String> questionIds) {
        String newId = UUID.randomUUID().toString();
        questionIds.put(originalId.toLowerCase(Locale.ROOT), newId);
        return newId;
    }

    private static Rubrica copyRubrica(Rubrica source, String newCaseId, Map<String, String> questionIds) {
        if (source == null) {
            return null;
        }
        String newRubricId = UUID.randomUUID().toString();
        var criterios = source.criterios().stream().map(criterio -> new CriterioRubrica(
                UUID.randomUUID().toString(), criterio.descripcion(), criterio.puntaje())).toList();
        if (!source.hasExplicitStructure()) {
            return new Rubrica(newRubricId, criterios);
        }
        var asociaciones = source.preguntas().stream().map(pregunta -> {
            String newQuestionId = questionIds.get(pregunta.idPregunta().toLowerCase(Locale.ROOT));
            if (newQuestionId == null) {
                throw new IllegalArgumentException("Cannot copy a rubric reference outside its case");
            }
            return new RubricaPregunta(UUID.randomUUID().toString(), newRubricId,
                    newQuestionId, pregunta.valorPregunta(), pregunta.criterio());
        }).collect(Collectors.toUnmodifiableSet());
        return new Rubrica(newRubricId, criterios, newCaseId, source.nombre(), source.descripcion(),
                source.puntajeTotal(), source.definicionEvaluacion(), null, asociaciones);
    }
}
