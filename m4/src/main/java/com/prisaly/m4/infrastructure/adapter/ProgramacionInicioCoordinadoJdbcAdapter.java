package com.prisaly.m4.infrastructure.adapter;

import com.prisaly.m4.api.ConsultaProgramacionNoDisponibleException;
import com.prisaly.m4.api.ContextoProgramacion;
import com.prisaly.m4.api.ProgramacionInicioCoordinadoPort;
import com.prisaly.m4.api.ProgramacionParticipacionPort;

import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/** Usa exclusivamente tablas propiedad de M4 y conserva la transacción llamadora. */
public class ProgramacionInicioCoordinadoJdbcAdapter implements ProgramacionInicioCoordinadoPort {
    private final JdbcTemplate jdbc;
    private final ProgramacionParticipacionPort consulta;

    public ProgramacionInicioCoordinadoJdbcAdapter(
            JdbcTemplate jdbc, ProgramacionParticipacionPort consulta) {
        this.jdbc = Objects.requireNonNull(jdbc);
        this.consulta = Objects.requireNonNull(consulta);
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public Optional<ContextoProgramacion> bloquearYConsultarConvocatoria(
            UUID convocatoriaId, UUID estudianteId) {
        Objects.requireNonNull(convocatoriaId);
        Objects.requireNonNull(estudianteId);
        if (!TransactionSynchronizationManager.isActualTransactionActive()
                || TransactionSynchronizationManager.isCurrentTransactionReadOnly()) {
            throw new ConsultaProgramacionNoDisponibleException();
        }
        try {
            // Una nueva sentencia tras esperar debe ver lo confirmado por el ganador.
            if (!"read committed"
                    .equals(
                            jdbc.queryForObject(
                                    "SELECT current_setting('transaction_isolation')",
                                    String.class))) {
                throw new ConsultaProgramacionNoDisponibleException();
            }
            var localizacion =
                    jdbc.queryForList(
                            "SELECT \"idSesion\" FROM public.\"Convocatoria\""
                                    + " WHERE \"idConvocatoria\"=? AND \"idEstudiante\"=?",
                            UUID.class,
                            convocatoriaId,
                            estudianteId);
            if (localizacion.isEmpty()) return Optional.empty();
            UUID sesionId = localizacion.getFirst();
            var sesiones =
                    jdbc.queryForList(
                            "SELECT \"idSesion\" FROM public.\"Sesion\" WHERE \"idSesion\"=? FOR"
                                    + " UPDATE",
                            UUID.class,
                            sesionId);
            if (sesiones.isEmpty()) throw new ConsultaProgramacionNoDisponibleException();
            var asignacion =
                    jdbc.queryForList(
                            "SELECT \"idSesion\" FROM public.\"Convocatoria\" WHERE"
                                    + " \"idConvocatoria\"=? AND \"idEstudiante\"=? FOR UPDATE",
                            UUID.class,
                            convocatoriaId,
                            estudianteId);
            // No continuar con una reserva de otra sesión ni con titularidad obsoleta.
            if (asignacion.size() != 1 || !sesionId.equals(asignacion.getFirst())) {
                throw new ConsultaProgramacionNoDisponibleException();
            }
            var contexto = consulta.consultarConvocatoria(convocatoriaId, estudianteId);
            if (contexto == null
                    || contexto.isEmpty()
                    || !sesionId.equals(contexto.get().sesionId())
                    || !convocatoriaId.equals(contexto.get().convocatoriaId())
                    || !estudianteId.equals(contexto.get().estudianteId())) {
                throw new ConsultaProgramacionNoDisponibleException();
            }
            return contexto;
        } catch (DataAccessException causa) {
            throw new ConsultaProgramacionNoDisponibleException(causa);
        }
    }
}
