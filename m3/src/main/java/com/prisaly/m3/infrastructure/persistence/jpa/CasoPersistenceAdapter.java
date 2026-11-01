package com.prisaly.m3.infrastructure.persistence.jpa;

import com.prisaly.m3.application.port.CasoRepositoryPort;
import com.prisaly.m3.domain.model.Caso;
import jakarta.persistence.EntityManagerFactory;
import java.sql.Connection;
import java.sql.SQLException;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Objects;
import java.util.Optional;
import org.hibernate.Session;
import org.springframework.orm.jpa.EntityManagerFactoryUtils;
import org.springframework.orm.jpa.JpaTransactionManager;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * M3-INC-10A: insert-only SQL writer plus the existing consistent JPA reader.
 * SQL is parameterized and runs on Hibernate's transaction-bound connection.
 * No mutable duplicate entities, JSON FormatMapper, cascades or schema changes.
 * Authorization remains the application/M1 responsibility; this is not an entry point.
 */
@Repository
public class CasoPersistenceAdapter implements CasoRepositoryPort {
    private final EntityManagerFactory entityManagerFactory;
    private final CasoJpaAggregateReader reader;
    private final TransactionTemplate writeTransaction;

    public CasoPersistenceAdapter(EntityManagerFactory entityManagerFactory,
            CasoJpaAggregateReader reader, PlatformTransactionManager transactionManager) {
        this.entityManagerFactory = Objects.requireNonNull(entityManagerFactory, "entityManagerFactory");
        this.reader = Objects.requireNonNull(reader, "reader");
        if (!(transactionManager instanceof JpaTransactionManager jpaTransactionManager)
                || jpaTransactionManager.getEntityManagerFactory() != entityManagerFactory) {
            throw new IllegalArgumentException("Writer must use the application's EntityManagerFactory and transaction manager");
        }
        writeTransaction = new TransactionTemplate(transactionManager); // REQUIRED: joins compatible caller transactions.
    }

    @Override
    public Optional<Caso> findById(String caseId) {
        // Preserves REQUIRES_NEW/REPEATABLE_READ: caller's uncommitted inserts are intentionally invisible.
        return reader.findById(CasoPersistenceMapper.uuid(caseId)).map(CasoPersistenceMapper::toDomain);
    }

    @Override
    public void insert(Caso caso) {
        CasoPersistenceMapper.validateInsert(caso);
        writeTransaction.executeWithoutResult(status -> {
            var em = Objects.requireNonNull(
                    EntityManagerFactoryUtils.getTransactionalEntityManager(entityManagerFactory),
                    "Transactional EntityManager is required");
            em.unwrap(Session.class).doWork(connection -> insertAggregate(connection, caso));
        });
    }

    @Override
    public void update(Caso caso) {
        throw new UnsupportedOperationException("M3-INC-10A supports insert/findById only; aggregate updates require approval");
    }

    private static void insertAggregate(Connection connection, Caso caso) throws SQLException {
        var caseId = CasoPersistenceMapper.uuid(caso.id());
        // Absent creation dates use V001's actual DEFAULT now(), never an invented Java date.
        execute(connection, """
                INSERT INTO public."Caso" ("idCaso", "idDocente", "nombre", "historia",
                    "desarrolloSituacional", "tiempoEstimadoSegundos", "estado", "activo",
                    "fechaCreacion", "fechaActualizacion")
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, %s, ?)
                """.formatted(creationExpression(caso.fechaCreacion())), caseId,
                CasoPersistenceMapper.uuid(caso.docenteId()), caso.titulo(), caso.historia(),
                caso.desarrolloSituacional(), caso.tiempoEstimadoSegundos(),
                CasoPersistenceMapper.state(caso.estado()), caso.activo(),
                creationParameters(caso.fechaCreacion(), caso.fechaActualizacion()));
        for (var escenario : caso.escenarios()) {
            var scenarioId = CasoPersistenceMapper.uuid(escenario.id());
            execute(connection, """
                    INSERT INTO public."Escenario" ("idEscenario", "idCaso", "titulo", "contexto",
                        "instrucciones", "orden", "activo") VALUES (?, ?, ?, ?, ?, ?, ?)
                    """, scenarioId, caseId, escenario.titulo(), escenario.contexto(),
                    escenario.instrucciones(), escenario.orden(), escenario.activo());
            for (var pregunta : escenario.preguntas()) {
                var questionId = CasoPersistenceMapper.uuid(pregunta.id());
                execute(connection, """
                        INSERT INTO public."Pregunta" ("idPregunta", "idEscenario", "enunciado",
                            "textoAyuda", "estadoEdicion", "visible", "orden", "fechaCreacion", "fechaActualizacion")
                        VALUES (?, ?, ?, ?, ?, ?, ?, %s, ?)
                        """.formatted(creationExpression(pregunta.fechaCreacion())), questionId,
                        scenarioId, pregunta.enunciado(), pregunta.textoAyuda(), pregunta.estadoEdicion(),
                        pregunta.visible(), pregunta.orden(),
                        creationParameters(pregunta.fechaCreacion(), pregunta.fechaActualizacion()));
                for (var opcion : pregunta.opciones()) {
                    execute(connection, """
                            INSERT INTO public."OpcionRespuesta" ("idOpcion", "idPregunta", "texto",
                                "esCorrecta", "explicacion", "orden") VALUES (?, ?, ?, ?, ?, ?)
                            """, CasoPersistenceMapper.uuid(opcion.id()), questionId, opcion.texto(),
                            opcion.esCorrecta(), opcion.explicacion(), opcion.orden());
                }
            }
        }
        var rubrica = caso.rubrica();
        if (rubrica != null) {
            var rubricId = CasoPersistenceMapper.uuid(rubrica.id());
            execute(connection, """
                    INSERT INTO public."Rubrica" ("idRubrica", "idCaso", "nombre", "descripcion",
                        "puntajeTotal", "definicionEvaluacion", "fechaCreacion")
                    VALUES (?, ?, ?, ?, ?, CAST(? AS jsonb), %s)
                    """.formatted(creationExpression(rubrica.fechaCreacion())), rubricId, caseId,
                    rubrica.nombre(), rubrica.descripcion(), rubrica.puntajeTotal(),
                    rubrica.definicionEvaluacion(), creationParameters(rubrica.fechaCreacion()));
            for (var association : rubrica.preguntas()) {
                execute(connection, """
                        INSERT INTO public."RubricaPregunta" ("idRubricaPregunta", "idRubrica",
                            "idPregunta", "valorPregunta", "criterio") VALUES (?, ?, ?, ?, ?)
                        """, CasoPersistenceMapper.uuid(association.idRubricaPregunta()), rubricId,
                        CasoPersistenceMapper.uuid(association.idPregunta()), association.valorPregunta(), association.criterio());
            }
        }
    }

    private static String creationExpression(Instant created) {
        return created == null ? "DEFAULT" : "?";
    }

    private static Object[] creationParameters(Instant created, Object... remaining) {
        Object[] parameters = new Object[remaining.length + (created == null ? 0 : 1)];
        int index = 0;
        if (created != null) {
            parameters[index++] = created;
        }
        System.arraycopy(remaining, 0, parameters, index, remaining.length);
        return parameters;
    }

    private static void execute(Connection connection, String sql, Object... parameters) throws SQLException {
        try (var statement = connection.prepareStatement(sql)) {
            int index = 1;
            for (Object value : parameters) {
                // A final Object[] carries the optional DEFAULT/explicit timestamp arguments.
                if (value instanceof Object[] tail) {
                    for (Object element : tail) {
                        statement.setObject(index++, jdbcValue(element));
                    }
                } else {
                    statement.setObject(index++, jdbcValue(value));
                }
            }
            if (statement.executeUpdate() != 1) {
                throw new SQLException("Expected exactly one inserted row");
            }
        }
    }

    private static Object jdbcValue(Object value) {
        return value instanceof Instant instant ? instant.atOffset(ZoneOffset.UTC) : value;
    }
}
