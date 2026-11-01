package com.prisaly.m4.infrastructure.adapter;

import com.prisaly.m4.application.port.CasoAcademicoPort;
import com.prisaly.m4.domain.rule.ReglaNegocioException;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.UUID;

/**
 * Protocolo SQL V007-V009: M4 lee metadatos aprobados y escribe SOLO Sesion. El trigger oficial es
 * el único escritor de fechaBloqueoAcademico.
 */
public final class CasoAcademicoBd0052Adapter implements CasoAcademicoPort {
    private final JdbcTemplate jdbc;

    public CasoAcademicoBd0052Adapter(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public boolean estaPublicado(UUID id) {
        boolean write =
                TransactionSynchronizationManager.isActualTransactionActive()
                        && !TransactionSynchronizationManager.isCurrentTransactionReadOnly();
        var rows =
                jdbc.query(
                        "SELECT \"estado\",\"activo\" FROM public.\"Caso\" WHERE \"idCaso\"=?"
                                + (write ? " FOR UPDATE" : ""),
                        (rs, n) -> "Publicado".equals(rs.getString(1)) && rs.getBoolean(2),
                        id);
        return rows.size() == 1 && rows.get(0);
    }

    public void solicitarBloqueoAcademico(UUID id) {
        if (!TransactionSynchronizationManager.isActualTransactionActive())
            throw new ReglaNegocioException(
                    "M4-BLOQUEO-ACADEMICO-NO-CONFIRMADO",
                    "Se requiere transacción de programación");
        Boolean marcado =
                jdbc.queryForObject(
                        "SELECT \"fechaBloqueoAcademico\" IS NOT NULL FROM public.\"Caso\" WHERE"
                            + " \"idCaso\"=?",
                        Boolean.class,
                        id);
        if (!Boolean.TRUE.equals(marcado))
            throw new ReglaNegocioException(
                    "M4-BLOQUEO-ACADEMICO-NO-CONFIRMADO", "El trigger oficial no confirmó F3-03");
    }
}
