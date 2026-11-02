package com.prisaly.m7.infrastructure;

import com.prisaly.m7.application.ReclamoEnvioPort;
import com.prisaly.m7.domain.EnvioReclamado;
import com.prisaly.m7.domain.EstadoEnvioCorreo;
import jakarta.persistence.EntityManager;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import org.springframework.transaction.annotation.Transactional;

/**
 * Claims and completes EnvioCorreo using PostgreSQL SKIP LOCKED and now() (D3, D10).
 * Result updates require the same lease timestamp set at claim time.
 * Abandoned leases at max attempts are finalized as Fallido without another SMTP call.
 */
public class ReclamoEnvioJpaAdapter implements ReclamoEnvioPort {

    private final EntityManager em;

    public ReclamoEnvioJpaAdapter(EntityManager em) {
        this.em = Objects.requireNonNull(em, "em");
    }

    @Override
    @Transactional
    public List<EnvioReclamado> reclamar(int lote, Duration lease, int maxIntentos) {
        if (lote < 1) {
            throw new IllegalArgumentException("lote must be >= 1");
        }
        if (lease == null || lease.isNegative() || lease.isZero()) {
            throw new IllegalArgumentException("lease must be positive");
        }
        if (maxIntentos < 1) {
            throw new IllegalArgumentException("maxIntentos must be >= 1");
        }

        @SuppressWarnings("unchecked")
        List<Object[]> candidatos = em.createNativeQuery(
                        """
                        SELECT e."idEnvioCorreo", e."numeroIntentos"
                        FROM "EnvioCorreo" e
                        WHERE e."estadoEnvio" = :pendiente
                          AND (e."proximoReintento" IS NULL OR e."proximoReintento" <= now())
                        ORDER BY e."fechaCreacion"
                        FOR UPDATE OF e SKIP LOCKED
                        LIMIT :lote
                        """)
                .setParameter("pendiente", EstadoEnvioCorreo.PENDIENTE)
                .setParameter("lote", lote)
                .getResultList();

        if (candidatos.isEmpty()) {
            return List.of();
        }

        List<EnvioReclamado> reclamados = new ArrayList<>();
        long leaseSeconds = lease.getSeconds();
        for (Object[] candidato : candidatos) {
            UUID id = (UUID) candidato[0];
            int intentosActuales = ((Number) candidato[1]).intValue();
            if (intentosActuales >= maxIntentos) {
                em.createNativeQuery(
                                """
                                UPDATE "EnvioCorreo"
                                SET "estadoEnvio" = :fallido,
                                    "ultimoError" = :error,
                                    "proximoReintento" = NULL
                                WHERE "idEnvioCorreo" = :id
                                  AND "estadoEnvio" = :pendiente
                                """)
                        .setParameter("fallido", EstadoEnvioCorreo.FALLIDO)
                        .setParameter(
                                "error",
                                "Lease expired after max attempts without recorded SMTP result")
                        .setParameter("id", id)
                        .setParameter("pendiente", EstadoEnvioCorreo.PENDIENTE)
                        .executeUpdate();
                continue;
            }

            @SuppressWarnings("unchecked")
            List<Object[]> filas = em.createNativeQuery(
                            """
                            UPDATE "EnvioCorreo"
                            SET "numeroIntentos" = "numeroIntentos" + 1,
                                "fechaUltimoIntento" = now(),
                                "proximoReintento" = now() + (:leaseSeconds * interval '1 second')
                            WHERE "idEnvioCorreo" = :id
                              AND "estadoEnvio" = :pendiente
                            RETURNING "idEnvioCorreo", "correoDestino", "asunto", "contenido",
                                      "numeroIntentos", "proximoReintento"
                            """)
                    .setParameter("leaseSeconds", leaseSeconds)
                    .setParameter("id", id)
                    .setParameter("pendiente", EstadoEnvioCorreo.PENDIENTE)
                    .getResultList();
            if (filas.size() != 1) {
                continue;
            }
            Object[] fila = filas.getFirst();
            reclamados.add(new EnvioReclamado(
                    (UUID) fila[0],
                    (String) fila[1],
                    (String) fila[2],
                    (String) fila[3],
                    ((Number) fila[4]).intValue(),
                    toInstant(fila[5])));
        }
        return List.copyOf(reclamados);
    }

    @Override
    @Transactional
    public boolean registrarAceptacion(UUID idEnvio, InstantLease lease, String messageId) {
        Objects.requireNonNull(idEnvio, "idEnvio");
        Objects.requireNonNull(lease, "lease");
        Objects.requireNonNull(messageId, "messageId");
        int actualizados = em.createNativeQuery(
                        """
                        UPDATE "EnvioCorreo"
                        SET "estadoEnvio" = :enviado,
                            "fechaEnvio" = now(),
                            "ultimoError" = :detalle,
                            "proximoReintento" = NULL
                        WHERE "idEnvioCorreo" = :id
                          AND "estadoEnvio" = :pendiente
                          AND "proximoReintento" = :leaseHasta
                          AND "numeroIntentos" = :intentos
                        """)
                .setParameter("enviado", EstadoEnvioCorreo.ENVIADO)
                .setParameter("detalle", "SMTP accepted messageId=" + messageId)
                .setParameter("id", idEnvio)
                .setParameter("pendiente", EstadoEnvioCorreo.PENDIENTE)
                .setParameter("leaseHasta", lease.leaseHasta())
                .setParameter("intentos", lease.numeroIntentos())
                .executeUpdate();
        return actualizados == 1;
    }

    @Override
    @Transactional
    public boolean registrarFallo(
            UUID idEnvio, InstantLease lease, String estadoFinal, String ultimoError, Duration proximoEn) {
        Objects.requireNonNull(idEnvio, "idEnvio");
        Objects.requireNonNull(lease, "lease");
        Objects.requireNonNull(estadoFinal, "estadoFinal");
        Objects.requireNonNull(ultimoError, "ultimoError");
        Objects.requireNonNull(proximoEn, "proximoEn");
        if (!EstadoEnvioCorreo.PENDIENTE.equals(estadoFinal) && !EstadoEnvioCorreo.FALLIDO.equals(estadoFinal)) {
            throw new IllegalArgumentException("estadoFinal must be Pendiente or Fallido");
        }

        int actualizados;
        if (EstadoEnvioCorreo.FALLIDO.equals(estadoFinal)) {
            actualizados = em.createNativeQuery(
                            """
                            UPDATE "EnvioCorreo"
                            SET "estadoEnvio" = :fallido,
                                "ultimoError" = :error,
                                "proximoReintento" = NULL
                            WHERE "idEnvioCorreo" = :id
                              AND "estadoEnvio" = :pendiente
                              AND "proximoReintento" = :leaseHasta
                              AND "numeroIntentos" = :intentos
                            """)
                    .setParameter("fallido", EstadoEnvioCorreo.FALLIDO)
                    .setParameter("error", ultimoError)
                    .setParameter("id", idEnvio)
                    .setParameter("pendiente", EstadoEnvioCorreo.PENDIENTE)
                    .setParameter("leaseHasta", lease.leaseHasta())
                    .setParameter("intentos", lease.numeroIntentos())
                    .executeUpdate();
        } else {
            actualizados = em.createNativeQuery(
                            """
                            UPDATE "EnvioCorreo"
                            SET "estadoEnvio" = :pendiente,
                                "ultimoError" = :error,
                                "proximoReintento" = now() + (:esperaSeconds * interval '1 second')
                            WHERE "idEnvioCorreo" = :id
                              AND "estadoEnvio" = :pendiente
                              AND "proximoReintento" = :leaseHasta
                              AND "numeroIntentos" = :intentos
                            """)
                    .setParameter("pendiente", EstadoEnvioCorreo.PENDIENTE)
                    .setParameter("error", ultimoError)
                    .setParameter("esperaSeconds", proximoEn.getSeconds())
                    .setParameter("id", idEnvio)
                    .setParameter("leaseHasta", lease.leaseHasta())
                    .setParameter("intentos", lease.numeroIntentos())
                    .executeUpdate();
        }
        return actualizados == 1;
    }

    private static Instant toInstant(Object value) {
        if (value instanceof Instant instant) {
            return instant;
        }
        if (value instanceof java.sql.Timestamp timestamp) {
            return timestamp.toInstant();
        }
        if (value instanceof java.time.OffsetDateTime odt) {
            return odt.toInstant();
        }
        throw new IllegalStateException("Unsupported timestamp type: " + value.getClass().getName());
    }
}
