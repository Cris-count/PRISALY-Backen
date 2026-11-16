package com.prisaly.m5.infrastructure.persistencia;

import com.prisaly.m5.application.PersistenciaIntentoException;
import com.prisaly.m5.application.ReferenciaIntento;
import com.prisaly.m5.application.fase.FaseAcademica;
import com.prisaly.m5.application.port.RepositorioFases;
import com.prisaly.m5.domain.intento.EstadoIntento;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.IllegalTransactionStateException;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * Adaptador JDBC de {@link RepositorioFases} sobre la tabla candidata {@code "FaseIntento"} (V018,
 * {@code db/sim-propuesta-b}; DC-BE-SIM-B1-B2A-24). Solo toca tablas de M5. Exige una transacción existente
 * ({@code MANDATORY}): nunca abre la suya.
 *
 * <p>No es un bean: {@code ConfiguracionParticipacion} lo construye únicamente con
 * {@code prisaly.m5.exploracion.fases.enabled=true}. Con el valor de producción ({@code false}) M5 no ejecuta ninguna
 * sentencia sobre {@code "FaseIntento"}, que no existe en el esquema productivo.
 */
public final class RepositorioFasesJdbc implements RepositorioFases {

    private static final String SELECCION = """
            SELECT i."estado", i."limiteTemporal", clock_timestamp() AS "ahora",
                   f."idGuion", f."fase", f."fechaInicioExploracion", f."fechaInicioCuestionario"
            FROM public."Intento" i
            LEFT JOIN public."FaseIntento" f ON f."idIntento" = i."idIntento"
            WHERE i."idIntento" = ?""";

    private final JdbcTemplate jdbc;

    public RepositorioFasesJdbc(JdbcTemplate jdbc) {
        this.jdbc = Objects.requireNonNull(jdbc, "jdbc");
    }

    @Override
    public Optional<IntentoConFase> leer(ReferenciaIntento intento) {
        return consultar(SELECCION, intento);
    }

    /**
     * Two statements on purpose. A single {@code SELECT ... LEFT JOIN "FaseIntento" ... FOR UPDATE OF i} re-reads only the
     * locked {@code "Intento"} row after waiting for a concurrent transaction, while the joined phase keeps the statement's
     * old snapshot: a second caller would still see {@code Exploracion} after the first one committed the questionnaire.
     * Locking first and reading afterwards (a new READ COMMITTED snapshot) always returns the committed phase.
     */
    @Override
    public Optional<IntentoConFase> bloquear(ReferenciaIntento intento) {
        exigirTransaccion();
        if (jdbc.queryForList("SELECT 1 FROM public.\"Intento\" WHERE \"idIntento\" = ? FOR UPDATE", Integer.class,
                id(intento)).isEmpty()) {
            return Optional.empty();
        }
        return consultar(SELECCION, intento);
    }

    @Override
    public void crear(ReferenciaIntento intento, UUID idGuion) {
        exigirTransaccion();
        jdbc.update("INSERT INTO public.\"FaseIntento\" (\"idIntento\", \"idGuion\", \"fase\") VALUES (?, ?, 'Exploracion')",
                id(intento), Objects.requireNonNull(idGuion, "idGuion"));
    }

    /**
     * QA33-03: el CTE {@code reloj} evalúa {@code clock_timestamp()} una sola vez, así que la comparación con el límite y
     * la fecha grabada son el mismo instante. Si el límite vence entre la lectura bloqueada y esta sentencia, no se
     * actualiza ninguna fila y la fase sigue en {@code Exploracion}.
     */
    @Override
    public Optional<Instant> marcarCuestionario(ReferenciaIntento intento) {
        exigirTransaccion();
        UUID id = id(intento);
        List<OffsetDateTime> fechas = jdbc.queryForList("""
                WITH reloj AS (SELECT clock_timestamp() AS "ahora")
                UPDATE public."FaseIntento" f
                SET "fase" = 'Cuestionario', "fechaInicioCuestionario" = reloj."ahora"
                FROM reloj, public."Intento" i
                WHERE f."idIntento" = ? AND f."fase" = 'Exploracion'
                  AND i."idIntento" = f."idIntento" AND reloj."ahora" <= i."limiteTemporal"
                RETURNING f."fechaInicioCuestionario\"""", OffsetDateTime.class, id);
        if (fechas.size() == 1) {
            return Optional.of(fechas.get(0).toInstant());
        }
        // La fila del intento está bloqueada por el llamador, así que la fase no pudo cambiar: solo el límite explica que
        // no se haya actualizado nada. Cualquier otra situación es una incoherencia de persistencia.
        List<String> fase = jdbc.queryForList("SELECT \"fase\" FROM public.\"FaseIntento\" WHERE \"idIntento\" = ?", String.class, id);
        if (!fase.equals(List.of("Exploracion"))) {
            throw new PersistenciaIntentoException("La fase no estaba en exploración al marcar el cuestionario");
        }
        return Optional.empty();
    }

    private Optional<IntentoConFase> consultar(String sql, ReferenciaIntento intento) {
        exigirTransaccion();
        List<IntentoConFase> filas = jdbc.query(sql, (rs, n) -> fila(rs), id(intento));
        return filas.stream().findFirst();
    }

    private static IntentoConFase fila(ResultSet rs) throws SQLException {
        EstadoIntento estado = switch (rs.getString("estado")) {
            case "EnCurso" -> EstadoIntento.EN_CURSO;
            case "Finalizado" -> EstadoIntento.FINALIZADO;
            default -> throw new PersistenciaIntentoException("Estado de intento desconocido");
        };
        Optional<Fase> fase = Optional.empty();
        String nombre = rs.getString("fase");
        if (nombre != null) {
            FaseAcademica valor = switch (nombre) {
                case "Exploracion" -> FaseAcademica.EXPLORACION;
                case "Cuestionario" -> FaseAcademica.CUESTIONARIO;
                default -> throw new PersistenciaIntentoException("Fase desconocida");
            };
            fase = Optional.of(new Fase(rs.getObject("idGuion", UUID.class), valor,
                    instante(rs, "fechaInicioExploracion"),
                    Optional.ofNullable(rs.getObject("fechaInicioCuestionario", OffsetDateTime.class)).map(OffsetDateTime::toInstant)));
        }
        return new IntentoConFase(estado, instante(rs, "limiteTemporal"), instante(rs, "ahora"), fase);
    }

    private static Instant instante(ResultSet rs, String columna) throws SQLException {
        OffsetDateTime valor = rs.getObject(columna, OffsetDateTime.class);
        if (valor == null) {
            throw new PersistenciaIntentoException("Columna temporal nula: " + columna);
        }
        return valor.toInstant();
    }

    private static UUID id(ReferenciaIntento intento) {
        try {
            return UUID.fromString(Objects.requireNonNull(intento, "intento").valor());
        } catch (IllegalArgumentException noEsUuid) {
            throw new PersistenciaIntentoException("Referencia de intento no válida");
        }
    }

    private static void exigirTransaccion() {
        if (!TransactionSynchronizationManager.isActualTransactionActive()) {
            throw new IllegalTransactionStateException("RepositorioFasesJdbc exige una transacción existente");
        }
    }
}
