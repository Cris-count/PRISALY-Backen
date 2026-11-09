package com.prisaly.m5.infrastructure.persistencia;

import com.prisaly.m5.application.PersistenciaIntentoException;
import java.sql.SQLException;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.dao.DataAccessException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.prisaly.m5.application.ReferenciaConvocatoria;
import com.prisaly.m5.application.ReferenciaIntento;
import com.prisaly.m5.application.RepeticionNoHabilitadaException;
import com.prisaly.m5.application.port.RepositorioParticipaciones;

/**
 * Adaptador JDBC de {@link RepositorioParticipaciones} sobre BD-005.2. Es el
 * único código que inserta en {@code "Participacion"} y en {@code "Intento"}.
 *
 * <p>Todas las operaciones exigen la transacción del caso de uso
 * ({@link Propagation#MANDATORY}), que abre
 * {@link com.prisaly.m5.infrastructure.FachadaParticipacionTransaccional}.
 *
 * <p><b>Concurrencia</b> (protocolo de V003/V005):
 * <ul>
 *   <li>{@code "Participacion"} se bloquea con {@code SELECT ... FOR UPDATE}
 *       antes de leer sus intentos. El trigger {@code tr_intento_autorizacion}
 *       vuelve a bloquearla al insertar, en la misma transacción, y después
 *       bloquea al estudiante: el orden es siempre participación → estudiante.</li>
 *   <li>La creación usa {@code INSERT ... ON CONFLICT ("idConvocatoria") DO
 *       NOTHING} sobre {@code uq_participacion_idconvocatoria}: una segunda
 *       solicitud espera a que termine la primera, no inserta y bloquea la
 *       participación ya confirmada.</li>
 * </ul>
 *
 * <p><b>Valores:</b> la participación se inserta solo con sus claves; su
 * {@code "estado"} queda en el valor por defecto de V001 ({@code 'Pendiente'}) y
 * sus fechas y resultado no se escriben (estados y transiciones pendientes). El
 * intento es el número 1, {@code 'EnCurso'} y sin autorización: la repetición no
 * está habilitada. V005 rechaza el intento si el estudiante ya tuvo otro sobre el
 * mismo caso, y ese rechazo se traduce a {@link RepeticionNoHabilitadaException}.
 */
@Repository
public class RepositorioParticipacionesJdbc implements RepositorioParticipaciones {

    static final String BLOQUEAR = """
            SELECT "idParticipacion" FROM public."Participacion"
            WHERE "idConvocatoria" = ?
            FOR UPDATE
            """;

    static final String CREAR_SI_NO_EXISTE = """
            INSERT INTO public."Participacion" ("idParticipacion", "idConvocatoria")
            VALUES (?, ?)
            ON CONFLICT ("idConvocatoria") DO NOTHING
            """;

    static final String INTENTO_EN_CURSO = """
            SELECT "idIntento", "inicio", "limiteTemporal" FROM public."Intento"
            WHERE "idParticipacion" = ? AND "estado" = 'EnCurso'
            """;

    static final String TIENE_INTENTOS =
            "SELECT EXISTS (SELECT 1 FROM public.\"Intento\" WHERE \"idParticipacion\" = ?)";

    static final String CREAR_PRIMER_INTENTO = """
            INSERT INTO public."Intento"
              ("idIntento", "idParticipacion", "numeroIntento", "inicio", "limiteTemporal", "estado")
            VALUES (?, ?, 1, ?, ?, 'EnCurso')
            """;

    /** Mensaje de {@code prisaly_validar_intento} (V005) cuando falta la autorización de repetición. */
    static final String RECHAZO_REPETICION_V005 = "Repetición exige autorización expresa";

    /** SQLSTATE de la excepción de V005: {@code check_violation}. */
    static final String CHECK_VIOLATION = "23514";

    private final JdbcTemplate jdbc;

    public RepositorioParticipacionesJdbc(JdbcTemplate jdbc) {
        if (jdbc == null) {
            throw new IllegalArgumentException("El repositorio de participaciones requiere la base");
        }
        this.jdbc = jdbc;
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public Optional<ReferenciaParticipacion> bloquear(ReferenciaConvocatoria convocatoria) {
        UUID id = uuid(convocatoria.valor(), "La convocatoria");
        try {
            List<UUID> filas = jdbc.queryForList(BLOQUEAR, UUID.class, id);
            return filas.stream().findFirst().map(p -> new ReferenciaParticipacion(p.toString()));
        } catch (DataAccessException e) {
            throw new PersistenciaIntentoException("No se pudo bloquear la participación", e);
        }
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public ReferenciaParticipacion crearOBloquear(ReferenciaConvocatoria convocatoria) {
        UUID id = uuid(convocatoria.valor(), "La convocatoria");
        try {
            jdbc.update(CREAR_SI_NO_EXISTE, UUID.randomUUID(), id);
        } catch (DataAccessException e) {
            throw new PersistenciaIntentoException("No se pudo crear la participación", e);
        }
        return bloquear(convocatoria).orElseThrow(
                () -> new PersistenciaIntentoException("La participación no existe después de crearla"));
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public Optional<IntentoEnCurso> intentoEnCurso(ReferenciaParticipacion participacion) {
        UUID id = uuid(participacion.valor(), "La participación");
        try {
            List<IntentoEnCurso> filas = jdbc.query(INTENTO_EN_CURSO, (fila, n) -> new IntentoEnCurso(
                    new ReferenciaIntento(fila.getObject("idIntento", UUID.class).toString()),
                    fila.getObject("inicio", OffsetDateTime.class).toInstant(),
                    fila.getObject("limiteTemporal", OffsetDateTime.class).toInstant()), id);
            if (filas.size() > 1) {
                throw new PersistenciaIntentoException("La participación tiene más de un intento EnCurso");
            }
            return filas.stream().findFirst();
        } catch (DataAccessException e) {
            throw new PersistenciaIntentoException("No se pudo consultar el intento en curso", e);
        }
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public boolean tieneIntentos(ReferenciaParticipacion participacion) {
        UUID id = uuid(participacion.valor(), "La participación");
        try {
            return Boolean.TRUE.equals(jdbc.queryForObject(TIENE_INTENTOS, Boolean.class, id));
        } catch (DataAccessException e) {
            throw new PersistenciaIntentoException("No se pudieron consultar los intentos", e);
        }
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public Instant ahora() {
        try {
            OffsetDateTime ahora = jdbc.queryForObject("SELECT clock_timestamp()", OffsetDateTime.class);
            if (ahora == null) {
                throw new PersistenciaIntentoException("PostgreSQL no devolvió la hora del servidor");
            }
            return ahora.toInstant();
        } catch (DataAccessException e) {
            throw new PersistenciaIntentoException("No se pudo obtener la hora del servidor", e);
        }
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public ReferenciaIntento crearPrimerIntento(
            ReferenciaParticipacion participacion, Instant inicio, Instant limiteTemporal) {
        UUID idParticipacion = uuid(participacion.valor(), "La participación");
        UUID idIntento = UUID.randomUUID();
        try {
            jdbc.update(CREAR_PRIMER_INTENTO, idIntento, idParticipacion,
                    inicio.atOffset(ZoneOffset.UTC), limiteTemporal.atOffset(ZoneOffset.UTC));
        } catch (DataIntegrityViolationException e) {
            if (esRechazoDeRepeticion(e)) {
                throw new RepeticionNoHabilitadaException();
            }
            // Otras restricciones (p. ej. ck_intento_tiempos): conflicto de datos genérico.
            throw e;
        } catch (DataAccessException e) {
            throw new PersistenciaIntentoException("No se pudo crear el intento", e);
        }
        return new ReferenciaIntento(idIntento.toString());
    }

    /** La excepción viene de {@code prisaly_validar_intento} (V005) por falta de autorización. */
    static boolean esRechazoDeRepeticion(Throwable error) {
        for (Throwable causa = error; causa != null; causa = causa.getCause()) {
            if (causa instanceof SQLException sql
                    && CHECK_VIOLATION.equals(sql.getSQLState())
                    && sql.getMessage() != null
                    && sql.getMessage().contains(RECHAZO_REPETICION_V005)) {
                return true;
            }
            if (causa.getCause() == causa) {
                break;
            }
        }
        return false;
    }

    private static UUID uuid(String valor, String dato) {
        try {
            return UUID.fromString(valor);
        } catch (IllegalArgumentException e) {
            throw new PersistenciaIntentoException(dato + " debe ser un identificador uuid de BD-005.2", e);
        }
    }
}
