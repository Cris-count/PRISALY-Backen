package com.prisaly.m3.infrastructure.persistence.jdbc;

import com.prisaly.m3.application.port.CasoListadoDocentePort;
import com.prisaly.m3.domain.model.CasoEstado;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;

/** DC-67 candidate: M3's own table only. Not a component: assembled only by {@code M3ListadoCasosConfig}. */
public final class CasoListadoDocenteJdbcAdapter implements CasoListadoDocentePort {
    private final JdbcTemplate jdbc;

    public CasoListadoDocenteJdbcAdapter(JdbcTemplate jdbc) {
        this.jdbc = Objects.requireNonNull(jdbc, "jdbc");
    }

    @Override
    public List<FilaCaso> listarPorDocente(UUID idDocente) {
        Objects.requireNonNull(idDocente, "idDocente");
        return jdbc.query("""
                SELECT "idCaso", "nombre", "tiempoEstimadoSegundos", "estado", "activo"
                FROM public."Caso" WHERE "idDocente" = ?
                ORDER BY "fechaCreacion" DESC, "idCaso"
                """, (rs, n) -> new FilaCaso(rs.getObject(1, UUID.class), rs.getString(2), rs.getInt(3),
                        estado(rs.getString(4)), rs.getBoolean(5)), idDocente);
    }

    private static CasoEstado estado(String almacenado) {
        return switch (almacenado) {
            case "Borrador" -> CasoEstado.BORRADOR;
            case "Publicado" -> CasoEstado.PUBLICADO;
            case "Archivado" -> CasoEstado.ARCHIVADO;
            default -> throw new IllegalStateException("Unsupported stored case state");
        };
    }
}
