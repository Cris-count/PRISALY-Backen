package com.prisaly.m5.infrastructure.persistencia;

import com.prisaly.m5.application.repeticion.AutorizacionRepeticionPort;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * Adaptador JDBC de {@code "AutorizacionRepeticion"} (A4). No es un componente de Spring: la repetición
 * sigue sin habilitarse (PF-08). Exige una transacción de escritura abierta; fuera de ella falla cerrado.
 * Las marcas temporales proceden de {@code clock_timestamp()} de PostgreSQL, el mismo reloj con el que
 * V005/V007 validan y consumen la autorización.
 */
public class AutorizacionRepeticionJdbc implements AutorizacionRepeticionPort {

    private final JdbcTemplate jdbc;

    public AutorizacionRepeticionJdbc(JdbcTemplate jdbc) {
        this.jdbc = Objects.requireNonNull(jdbc);
    }

    @Override
    public Optional<UUID> bloquearParticipacion(UUID idParticipacion) {
        exigirTransaccionDeEscritura();
        List<UUID> filas = jdbc.queryForList(
                "SELECT \"idConvocatoria\" FROM public.\"Participacion\" WHERE \"idParticipacion\" = ? FOR UPDATE",
                UUID.class, idParticipacion);
        return filas.stream().findFirst();
    }

    @Override
    public boolean existeVigente(UUID idParticipacion) {
        exigirTransaccionDeEscritura();
        return Boolean.TRUE.equals(jdbc.queryForObject("""
                SELECT EXISTS (SELECT 1 FROM public."AutorizacionRepeticion"
                               WHERE "idParticipacion" = ? AND "estadoAutorizacion" = 'Vigente'
                                 AND ("fechaVencimiento" IS NULL OR "fechaVencimiento" > clock_timestamp()))
                """, Boolean.class, idParticipacion));
    }

    @Override
    public AutorizacionRegistrada registrar(UUID idParticipacion, UUID idDocenteAutorizador, String motivo,
            Duration vigencia) {
        exigirTransaccionDeEscritura();
        UUID id = UUID.randomUUID();
        Double segundos = vigencia == null ? null : vigencia.toNanos() / 1_000_000_000d;
        return jdbc.queryForObject("""
                WITH reloj AS (SELECT clock_timestamp() AS ahora)
                INSERT INTO public."AutorizacionRepeticion"
                    ("idAutorizacion", "idParticipacion", "idDocenteAutorizador", "motivo",
                     "fechaAutorizacion", "fechaVencimiento", "estadoAutorizacion")
                SELECT ?, ?, ?, ?, reloj.ahora,
                       reloj.ahora + make_interval(secs => CAST(? AS double precision)), 'Vigente'
                FROM reloj
                RETURNING "fechaAutorizacion", "fechaVencimiento", "estadoAutorizacion"
                """, (rs, n) -> new AutorizacionRegistrada(id, idParticipacion, idDocenteAutorizador,
                        Optional.ofNullable(motivo), rs.getObject(1, OffsetDateTime.class).toInstant(),
                        Optional.ofNullable(rs.getObject(2, OffsetDateTime.class)).map(OffsetDateTime::toInstant),
                        rs.getString(3)),
                id, idParticipacion, idDocenteAutorizador, motivo, segundos);
    }

    private static void exigirTransaccionDeEscritura() {
        if (!TransactionSynchronizationManager.isActualTransactionActive()
                || TransactionSynchronizationManager.isCurrentTransactionReadOnly()) {
            throw new IllegalStateException("La autorización de repetición requiere una transacción de escritura");
        }
    }
}
