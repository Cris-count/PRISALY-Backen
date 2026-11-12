package com.prisaly.m7.infrastructure;

import com.prisaly.m7.application.EntregaCodigoAccesoRepositorio;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;

/** JDBC Outbox over candidate V016. Each statement is atomic; claims use {@code FOR UPDATE SKIP LOCKED}. */
public class EntregaCodigoAccesoJdbc implements EntregaCodigoAccesoRepositorio {

    private final JdbcTemplate jdbc;

    public EntregaCodigoAccesoJdbc(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public List<EntregaReclamada> reclamar(int lote, Duration lease, Instant ahora) {
        return jdbc.query("""
                UPDATE "EntregaCodigoAcceso" e SET "reclamadaHasta" = ?
                WHERE e."idEntrega" IN (
                  SELECT "idEntrega" FROM "EntregaCodigoAcceso"
                  WHERE "estado" = 'PENDIENTE' AND "proximoIntento" <= ?
                    AND ("reclamadaHasta" IS NULL OR "reclamadaHasta" < ?)
                  ORDER BY "proximoIntento" LIMIT ? FOR UPDATE SKIP LOCKED)
                RETURNING e."idEntrega", e."challengeId", e."cargaCifrada", e."nonce", e."kidCifrado", e."intentos",
                  e."fechaCreacion", e."fechaExpiracion"
                """, (rs, row) -> new EntregaReclamada(rs.getObject("idEntrega", UUID.class),
                        rs.getObject("challengeId", UUID.class).toString(), rs.getBytes("cargaCifrada"),
                        rs.getBytes("nonce"), rs.getString("kidCifrado"), rs.getInt("intentos"),
                        rs.getTimestamp("fechaCreacion").toInstant(), rs.getTimestamp("fechaExpiracion").toInstant()),
                ts(ahora.plus(lease)), ts(ahora), ts(ahora), lote);
    }

    @Override
    public void marcarEnviada(UUID idEntrega, Instant ahora) {
        cerrar(idEntrega, "ENVIADA", null, ahora);
    }

    @Override
    public void reprogramar(UUID idEntrega, int intentos, Instant proximoIntento, String codigoError) {
        jdbc.update("""
                UPDATE "EntregaCodigoAcceso" SET "intentos" = ?, "proximoIntento" = ?, "codigoError" = ?,
                  "reclamadaHasta" = NULL
                WHERE "idEntrega" = ? AND "estado" = 'PENDIENTE'
                """, intentos, ts(proximoIntento), acotar(codigoError), idEntrega);
    }

    @Override
    public void finalizarSinEnvio(UUID idEntrega, EstadoFinal estado, String codigoError, Instant ahora) {
        cerrar(idEntrega, estado.name(), codigoError, ahora);
    }

    @Override
    public int cancelarSinTiempo(Instant umbral, Instant ahora) {
        return jdbc.update("""
                UPDATE "EntregaCodigoAcceso" SET "estado" = 'CANCELADA_VENCIDA', "cargaCifrada" = NULL, "nonce" = NULL,
                  "kidCifrado" = NULL, "fechaFinal" = ?, "codigoError" = 'SIN_TIEMPO', "reclamadaHasta" = NULL
                WHERE "estado" = 'PENDIENTE' AND "fechaExpiracion" <= ?
                """, ts(ahora), ts(umbral));
    }

    @Override
    public int purgarMetadatos(Instant limite) {
        return jdbc.update("DELETE FROM \"EntregaCodigoAcceso\" WHERE \"estado\" <> 'PENDIENTE' AND \"fechaCreacion\" < ?",
                ts(limite));
    }

    private void cerrar(UUID idEntrega, String estado, String codigoError, Instant ahora) {
        jdbc.update("""
                UPDATE "EntregaCodigoAcceso" SET "estado" = ?, "cargaCifrada" = NULL, "nonce" = NULL, "kidCifrado" = NULL,
                  "fechaFinal" = ?, "codigoError" = ?, "reclamadaHasta" = NULL
                WHERE "idEntrega" = ? AND "estado" = 'PENDIENTE'
                """, estado, ts(ahora), acotar(codigoError), idEntrega);
    }

    private static String acotar(String valor) {
        return valor == null ? null : valor.substring(0, Math.min(40, valor.length()));
    }

    private static Timestamp ts(Instant i) {
        return Timestamp.from(i);
    }
}
