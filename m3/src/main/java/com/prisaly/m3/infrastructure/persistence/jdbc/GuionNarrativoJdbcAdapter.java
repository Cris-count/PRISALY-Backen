package com.prisaly.m3.infrastructure.persistence.jdbc;

import com.prisaly.m3.api.GuionNarrativoEstudiantilPort;
import com.prisaly.m3.api.GuionNarrativoNoDisponibleException;
import com.prisaly.m3.application.port.GuionNarrativoRepositorio;
import com.prisaly.m3.infrastructure.persistence.jpa.JsonbCanonicalizer;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.util.HexFormat;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Persistencia candidata del guion narrativo sobre {@code "GuionNarrativoCaso"} (V017, {@code db/sim-propuesta}).
 *
 * <p>La huella es SHA-256 de la forma canónica tipada de {@link JsonbCanonicalizer}: estable frente al formato del
 * documento y al reformateo que PostgreSQL aplica a jsonb. Cada lectura la recalcula y la compara con la persistida.
 * Todas las operaciones exigen la transacción del invocador. No es un componente escaneado: lo registra
 * {@code M3NarrativaConfig} solo con el interruptor activo.
 */
public class GuionNarrativoJdbcAdapter implements GuionNarrativoRepositorio, GuionNarrativoEstudiantilPort {
    private static final String COLUMNAS = """
            SELECT "idGuion", "idCaso", "version", "estado", "definicion"::text, "hashDefinicion", "idDocenteAutor",
                   "fechaActualizacion", "fechaPublicacion"
            FROM public."GuionNarrativoCaso"
            """;

    private final JdbcTemplate jdbc;

    public GuionNarrativoJdbcAdapter(JdbcTemplate jdbc) {
        this.jdbc = Objects.requireNonNull(jdbc);
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public Optional<CasoNarrativa> bloquearCaso(UUID idCaso) {
        return jdbc.query("""
                SELECT "idCaso", "idDocente", "estado", "activo", "fechaBloqueoAcademico" IS NOT NULL
                FROM public."Caso" WHERE "idCaso" = ? FOR UPDATE
                """, (rs, n) -> new CasoNarrativa(rs.getObject(1, UUID.class), rs.getObject(2, UUID.class),
                rs.getString(3), rs.getBoolean(4), rs.getBoolean(5)), idCaso).stream().findFirst();
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public Optional<GuionGuardado> borrador(UUID idCaso) {
        return uno(COLUMNAS + " WHERE \"idCaso\" = ? AND \"estado\" = 'Borrador'", idCaso);
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public Optional<GuionGuardado> publicadoVigente(UUID idCaso) {
        return uno(COLUMNAS + " WHERE \"idCaso\" = ? AND \"estado\" = 'Publicado'", idCaso);
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public GuionGuardado guardarBorrador(UUID idCaso, UUID autor, String documentoJson) {
        String hash = huella(documentoJson);
        Optional<GuionGuardado> existente = borrador(idCaso);
        if (existente.isPresent()) {
            jdbc.update("""
                    UPDATE public."GuionNarrativoCaso" SET "definicion" = CAST(? AS jsonb), "hashDefinicion" = ?,
                           "idDocenteAutor" = ?, "fechaActualizacion" = clock_timestamp()
                    WHERE "idGuion" = ?
                    """, documentoJson, hash, autor, existente.get().idGuion());
            return porId(existente.get().idGuion()).orElseThrow();
        }
        UUID id = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO public."GuionNarrativoCaso" ("idGuion", "idCaso", "version", "estado", "definicion",
                    "hashDefinicion", "idDocenteAutor")
                SELECT ?, ?, COALESCE(MAX("version"), 0) + 1, 'Borrador', CAST(? AS jsonb), ?, ?
                FROM public."GuionNarrativoCaso" WHERE "idCaso" = ?
                """, id, idCaso, documentoJson, hash, autor, idCaso);
        return porId(id).orElseThrow();
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public GuionGuardado publicarBorrador(UUID idCaso) {
        jdbc.update("UPDATE public.\"GuionNarrativoCaso\" SET \"estado\" = 'Archivado' WHERE \"idCaso\" = ? AND \"estado\" = 'Publicado'",
                idCaso);
        List<UUID> publicado = jdbc.queryForList("""
                UPDATE public."GuionNarrativoCaso" SET "estado" = 'Publicado', "fechaPublicacion" = clock_timestamp()
                WHERE "idCaso" = ? AND "estado" = 'Borrador' RETURNING "idGuion"
                """, UUID.class, idCaso);
        if (publicado.size() != 1) { throw new IllegalStateException("No draft to publish"); }
        return porId(publicado.get(0)).orElseThrow();
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public Optional<GuionPublicado> publicado(UUID idGuion) {
        return leerPublicado(COLUMNAS + " WHERE \"idGuion\" = ? AND \"estado\" IN ('Publicado', 'Archivado')", idGuion);
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public Optional<GuionPublicado> publicadoDelCaso(UUID idCaso) {
        return leerPublicado(COLUMNAS + " WHERE \"idCaso\" = ? AND \"estado\" = 'Publicado'", idCaso);
    }

    private Optional<GuionPublicado> leerPublicado(String sql, UUID id) {
        try {
            return uno(sql, id).map(g -> new GuionPublicado(g.idGuion(), g.idCaso(), g.version(), g.hash(), g.definicionJson()));
        } catch (DataAccessException | IllegalStateException fallo) {
            throw new GuionNarrativoNoDisponibleException(fallo);
        }
    }

    private Optional<GuionGuardado> porId(UUID idGuion) {
        return uno(COLUMNAS + " WHERE \"idGuion\" = ?", idGuion);
    }

    private Optional<GuionGuardado> uno(String sql, UUID id) {
        return jdbc.query(sql, (rs, n) -> fila(rs), id).stream().findFirst();
    }

    private static GuionGuardado fila(ResultSet rs) throws SQLException {
        String definicion = rs.getString(5);
        String hash = rs.getString(6).trim();
        if (!hash.equals(huella(definicion))) {
            // The stored document no longer matches its fingerprint: never serve it.
            throw new IllegalStateException("Narrative script fingerprint mismatch");
        }
        OffsetDateTime publicacion = rs.getObject(9, OffsetDateTime.class);
        return new GuionGuardado(rs.getObject(1, UUID.class), rs.getObject(2, UUID.class), rs.getInt(3), rs.getString(4),
                definicion, hash, rs.getObject(7, UUID.class), rs.getObject(8, OffsetDateTime.class).toInstant(),
                Optional.ofNullable(publicacion).map(OffsetDateTime::toInstant));
    }

    static String huella(String documentoJson) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(JsonbCanonicalizer.canonicalize(documentoJson).getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException imposible) {
            throw new IllegalStateException(imposible);
        }
    }
}
