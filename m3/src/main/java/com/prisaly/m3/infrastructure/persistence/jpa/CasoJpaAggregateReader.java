package com.prisaly.m3.infrastructure.persistence.jpa;

import com.prisaly.m3.infrastructure.persistence.jpa.CasoJpaAggregateSnapshot.CasoRow;
import com.prisaly.m3.infrastructure.persistence.jpa.CasoJpaAggregateSnapshot.EscenarioRow;
import com.prisaly.m3.infrastructure.persistence.jpa.CasoJpaAggregateSnapshot.OpcionRespuestaRow;
import com.prisaly.m3.infrastructure.persistence.jpa.CasoJpaAggregateSnapshot.PreguntaRow;
import com.prisaly.m3.infrastructure.persistence.jpa.CasoJpaAggregateSnapshot.RubricaPreguntaRow;
import com.prisaly.m3.infrastructure.persistence.jpa.CasoJpaAggregateSnapshot.RubricaRow;
import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.hibernate.CacheMode;
import org.hibernate.Session;
import org.springframework.orm.jpa.EntityManagerFactoryUtils;
import org.springframework.orm.jpa.JpaTransactionManager;
import org.springframework.stereotype.Component;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

/** Internal read-only infrastructure component; does not implement CasoRepositoryPort. */
@Component
public final class CasoJpaAggregateReader {

    private final EntityManagerFactory entityManagerFactory;
    private final TransactionTemplate readTransaction;

    public CasoJpaAggregateReader(EntityManagerFactory entityManagerFactory) {
        this.entityManagerFactory = Objects.requireNonNull(entityManagerFactory, "entityManagerFactory");
        readTransaction = new TransactionTemplate(new JpaTransactionManager(entityManagerFactory));
        readTransaction.setReadOnly(true);
        readTransaction.setIsolationLevel(TransactionDefinition.ISOLATION_REPEATABLE_READ);
        // Do not silently join READ COMMITTED or reuse an already-populated persistence context.
        // Consequently, uncommitted work in a caller transaction is intentionally not visible.
        readTransaction.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    public Optional<CasoJpaAggregateSnapshot> findById(UUID caseId) {
        Objects.requireNonNull(caseId, "caseId");
        return Objects.requireNonNull(readTransaction.execute(status -> {
            EntityManager em = Objects.requireNonNull(
                    EntityManagerFactoryUtils.getTransactionalEntityManager(entityManagerFactory),
                    "Transactional EntityManager is required");
            // All data must come from this database snapshot, not second-level/query caches.
            em.unwrap(Session.class).setCacheMode(CacheMode.IGNORE);
            return read(em, caseId);
        }));
    }

    private Optional<CasoJpaAggregateSnapshot> read(EntityManager em, UUID caseId) {
        CasoJpaEntity caso = em.find(CasoJpaEntity.class, caseId);
        if (caso == null) {
            return Optional.empty();
        }
        List<EscenarioRow> escenarios = em.createQuery("""
                select e from EscenarioJpaEntity e where e.idCaso = :caseId order by e.orden
                """, EscenarioJpaEntity.class).setParameter("caseId", caseId).getResultList().stream()
                .map(e -> new EscenarioRow(e.getIdEscenario(), e.getIdCaso(), e.getTitulo(),
                        e.getContexto(), e.getInstrucciones(), e.getOrden(), e.isActivo())).toList();
        List<PreguntaRow> preguntas = em.createQuery("""
                select p from PreguntaJpaEntity p
                where p.idEscenario in (select e.idEscenario from EscenarioJpaEntity e where e.idCaso = :caseId)
                order by p.idEscenario, p.orden
                """, PreguntaJpaEntity.class).setParameter("caseId", caseId).getResultList().stream()
                .map(p -> new PreguntaRow(p.getIdPregunta(), p.getIdEscenario(), p.getEnunciado(),
                        p.getTextoAyuda(), p.getEstadoEdicion(), p.isVisible(), p.getOrden(),
                        p.getFechaCreacion(), p.getFechaActualizacion())).toList();
        List<OpcionRespuestaRow> opciones = em.createQuery("""
                select o from OpcionRespuestaJpaEntity o
                where o.idPregunta in (select p.idPregunta from PreguntaJpaEntity p
                    where p.idEscenario in (select e.idEscenario from EscenarioJpaEntity e where e.idCaso = :caseId))
                order by o.idPregunta, o.orden
                """, OpcionRespuestaJpaEntity.class).setParameter("caseId", caseId).getResultList().stream()
                .map(o -> new OpcionRespuestaRow(o.getIdOpcion(), o.getIdPregunta(), o.getTexto(),
                        o.isEsCorrecta(), o.getExplicacion(), o.getOrden())).toList();
        List<RubricaJpaEntity> rubricas = em.createQuery("""
                select r from RubricaJpaEntity r where r.idCaso = :caseId
                """, RubricaJpaEntity.class).setParameter("caseId", caseId).getResultList();
        if (rubricas.size() > 1) {
            throw new IllegalStateException("BD-005.2 permits at most one rubric per case");
        }
        RubricaRow rubrica = rubricas.isEmpty() ? null : rubricRow(rubricas.getFirst());
        Set<RubricaPreguntaRow> asociaciones = rubrica == null ? Set.of() : em.createQuery("""
                select rp from RubricaPreguntaJpaEntity rp where rp.idRubrica = :rubricId
                """, RubricaPreguntaJpaEntity.class).setParameter("rubricId", rubrica.idRubrica())
                .getResultList().stream().map(rp -> new RubricaPreguntaRow(rp.getIdRubricaPregunta(),
                        rp.getIdRubrica(), rp.getIdPregunta(), rp.getValorPregunta(), rp.getCriterio()))
                .collect(Collectors.toUnmodifiableSet());
        return Optional.of(new CasoJpaAggregateSnapshot(
                new CasoRow(caso.getIdCaso(), caso.getIdDocente(), caso.getNombre(), caso.getHistoria(),
                        caso.getDesarrolloSituacional(), caso.getTiempoEstimadoSegundos(), caso.getEstado(),
                        caso.isActivo(), caso.getFechaBloqueoAcademico(), caso.getFechaCreacion(),
                        caso.getFechaActualizacion()), escenarios, preguntas, opciones, rubrica, asociaciones));
    }

    private static RubricaRow rubricRow(RubricaJpaEntity r) {
        return new RubricaRow(r.getIdRubrica(), r.getIdCaso(), r.getNombre(), r.getDescripcion(),
                r.getPuntajeTotal(), r.getDefinicionEvaluacion(), r.getFechaCreacion());
    }
}
