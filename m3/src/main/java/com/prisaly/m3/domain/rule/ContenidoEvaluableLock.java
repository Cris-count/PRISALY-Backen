package com.prisaly.m3.domain.rule;

import com.prisaly.m3.domain.model.Caso;
import com.prisaly.m3.domain.model.CasoEstado;
import com.prisaly.m3.domain.model.CriterioRubrica;
import com.prisaly.m3.domain.model.Escenario;
import com.prisaly.m3.domain.model.OpcionRespuesta;
import com.prisaly.m3.domain.model.Pregunta;
import com.prisaly.m3.domain.model.Rubrica;
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
        if (source.id().equals(newId)) {
            throw new CasoDomainException(
                    CODE_COPY_CASE_ID_EQUALS_SOURCE,
                    "Independent copy must have a different case id");
        }
        CasoValidation.assertNotArchived(source);
        // M3-INC-09B: copying dates or assigning new ones is not an approved policy.
        // Preserve the existing transient-copy behavior; fail explicitly for dated sources.
        if (source.fechaCreacion() != null || source.fechaActualizacion() != null) {
            throw new CasoDomainException(CODE_COPY_METADATA_POLICY_PENDING,
                    "Copy creation/update metadata requires an approved policy");
        }
        return new Caso(
                newId,
                source.titulo(),
                source.docenteId(),
                source.tiempoEstimadoSegundos(),
                CasoEstado.BORRADOR,
                source.escenarios().stream().map(escenario -> new Escenario(
                        UUID.randomUUID().toString(),
                        escenario.descripcion(),
                        escenario.preguntas().stream().map(pregunta -> new Pregunta(
                                UUID.randomUUID().toString(),
                                pregunta.enunciado(),
                                pregunta.opciones().stream().map(opcion -> new OpcionRespuesta(
                                        UUID.randomUUID().toString(),
                                        opcion.texto(),
                                        opcion.esCorrecta())).toList())).toList())).toList(),
                source.rubrica() == null ? null : new Rubrica(
                        UUID.randomUUID().toString(),
                        source.rubrica().criterios().stream().map(criterio -> new CriterioRubrica(
                                UUID.randomUUID().toString(),
                                criterio.descripcion(),
                                criterio.puntaje())).toList()),
                false,
                source.historia(),
                source.desarrolloSituacional(),
                source.activo(),
                null,
                null,
                null);
    }
}
