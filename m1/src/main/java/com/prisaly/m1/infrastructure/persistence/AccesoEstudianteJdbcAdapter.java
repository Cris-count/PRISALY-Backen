package com.prisaly.m1.infrastructure.persistence;

import com.prisaly.m1.application.port.AccesoEstudiantePort;
import com.prisaly.m1.application.port.RevocacionCodigoAccesoPort;
import com.prisaly.m1.domain.student.PoliticaCodigoAcceso;
import com.prisaly.m1.domain.student.PoliticaSesionEstudiante;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * DD-02 alternative B over the candidate V014-V016 tables. Registered only with the student-access flag, so the
 * shipped configuration never touches tables that the canonical Flyway does not create.
 */
@Repository
@ConditionalOnProperty(name = "prisaly.m1.student-access.enabled", havingValue = "true")
public class AccesoEstudianteJdbcAdapter implements AccesoEstudiantePort, RevocacionCodigoAccesoPort {

    private final JdbcTemplate jdbc;

    public AccesoEstudianteJdbcAdapter(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public Optional<VinculoEstudiante> vinculoPorCorreo(String correoNormalizado) {
        return jdbc.query("""
                SELECT e."idUsuario", e."idEstudiante", u."correo", u."estado"
                FROM "Usuario" u JOIN "Estudiante" e ON e."idUsuario" = u."idUsuario"
                WHERE lower(u."correo") = ?
                """, (rs, row) -> mapVinculo(rs), correoNormalizado).stream().findFirst();
    }

    @Override
    public Optional<VinculoEstudiante> vinculo(UUID idUsuario) {
        return jdbc.query("""
                SELECT e."idUsuario", e."idEstudiante", u."correo", u."estado"
                FROM "Estudiante" e JOIN "Usuario" u ON u."idUsuario" = e."idUsuario"
                WHERE e."idUsuario" = ?
                """, (rs, row) -> mapVinculo(rs), idUsuario).stream().findFirst();
    }

    @Override
    public void revocarCredencialesAbiertas(UUID idUsuario, OffsetDateTime cuando) {
        jdbc.update("""
                UPDATE "CredencialAccesoEstudiante" SET "fechaRevocacion" = GREATEST(?, "fechaCreacion")
                WHERE "idUsuario" = ? AND "fechaConsumo" IS NULL AND "fechaRevocacion" IS NULL
                """, marca(cuando), idUsuario);
    }

    @Override
    public void guardarCredencial(CredencialNueva c) {
        jdbc.update("""
                INSERT INTO "CredencialAccesoEstudiante" ("idCredencial", "idUsuario", "idEstudiante", "challengeId",
                  "codigoHmac", "kidCodigo", "fechaCreacion", "fechaExpiracion")
                VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                """, c.idCredencial(), c.userId(), c.studentId(), c.challengeId(), c.codigoHmac(), c.kidCodigo(),
                marca(c.creada()), marca(c.expira()));
    }

    @Override
    public Optional<CredencialGuardada> bloquearCredencial(UUID challengeId) {
        return jdbc.query("""
                SELECT "challengeId", "idUsuario", "idEstudiante", "codigoHmac", "kidCodigo", "fechaExpiracion",
                  "fechaConsumo" IS NOT NULL AS consumida, "fechaRevocacion" IS NOT NULL AS revocada, "intentosFallidos"
                FROM "CredencialAccesoEstudiante" WHERE "challengeId" = ? FOR UPDATE
                """, (rs, row) -> new CredencialGuardada(
                        rs.getObject("challengeId", UUID.class), rs.getObject("idUsuario", UUID.class),
                        rs.getObject("idEstudiante", UUID.class), rs.getBytes("codigoHmac"), rs.getString("kidCodigo"),
                        tiempo(rs, "fechaExpiracion"), rs.getBoolean("consumida"), rs.getBoolean("revocada"),
                        rs.getInt("intentosFallidos")), challengeId).stream().findFirst();
    }

    @Override
    public void registrarIntentoFallido(UUID challengeId) {
        jdbc.update("""
                UPDATE "CredencialAccesoEstudiante" SET "intentosFallidos" = "intentosFallidos" + 1
                WHERE "challengeId" = ? AND "intentosFallidos" < ?
                """, challengeId, PoliticaCodigoAcceso.INTENTOS_MAXIMOS);
    }

    @Override
    public boolean consumir(UUID challengeId, OffsetDateTime ahora) {
        return jdbc.update("""
                UPDATE "CredencialAccesoEstudiante" SET "fechaConsumo" = GREATEST(?, "fechaCreacion")
                WHERE "challengeId" = ? AND "fechaConsumo" IS NULL AND "fechaRevocacion" IS NULL
                  AND "fechaExpiracion" > ? AND "intentosFallidos" < ?
                """, marca(ahora), challengeId, marca(ahora), PoliticaCodigoAcceso.INTENTOS_MAXIMOS) == 1;
    }

    @Override
    public void abrirSesion(SesionNueva s) {
        jdbc.update("""
                INSERT INTO "SesionEstudiante" ("idSesionEstudiante", "idUsuario", "idEstudiante", "sesionTokenHmac",
                  "kidSesion", "fechaCreacion", "fechaExpiracion", "fechaUltimaActividad")
                VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                """, s.idSesion(), s.userId(), s.studentId(), s.tokenHmac(), s.kidSesion(), marca(s.creada()),
                marca(s.expira()), marca(s.creada()));
    }

    // Validates and records activity in one statement (no check-then-act race). Own transaction so callers inside
    // read-only transactions can still resolve the identity (same behaviour as the former adapter).
    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public Optional<SesionGuardada> sesionVigente(List<byte[]> candidatos, OffsetDateTime ahora) {
        Timestamp marca = marca(ahora);
        Timestamp actividadMinima = marca(ahora.minus(PoliticaSesionEstudiante.INACTIVIDAD_MAXIMA));
        for (byte[] hmac : candidatos) {
            Optional<SesionGuardada> sesion = jdbc.query("""
                    UPDATE "SesionEstudiante" SET "fechaUltimaActividad" = GREATEST("fechaUltimaActividad", ?)
                    WHERE "sesionTokenHmac" = ? AND "fechaRevocacion" IS NULL AND "fechaExpiracion" > ?
                      AND "fechaUltimaActividad" > ?
                    RETURNING "idUsuario", "idEstudiante"
                    """, (rs, row) -> new SesionGuardada(rs.getObject("idUsuario", UUID.class),
                            rs.getObject("idEstudiante", UUID.class)), marca, hmac, marca, actividadMinima)
                    .stream().findFirst();
            if (sesion.isPresent()) {
                return sesion;
            }
        }
        return Optional.empty();
    }

    @Override
    public Optional<UUID> revocarSesion(List<byte[]> candidatos, OffsetDateTime cuando) {
        Optional<UUID> revocada = Optional.empty();
        for (byte[] hmac : candidatos) {
            List<UUID> filas = jdbc.queryForList("""
                    UPDATE "SesionEstudiante" SET "fechaRevocacion" = GREATEST(?, "fechaCreacion")
                    WHERE "sesionTokenHmac" = ? AND "fechaRevocacion" IS NULL
                    RETURNING "idEstudiante"
                    """, UUID.class, marca(cuando), hmac);
            if (!filas.isEmpty()) {
                revocada = Optional.of(filas.get(0));
            }
        }
        return revocada;
    }

    @Override
    public void auditar(String evento, UUID idEstudiante, String resultado, String origen, OffsetDateTime cuando) {
        String origenAcotado = origen == null ? "desconocido" : origen.substring(0, Math.min(64, origen.length()));
        jdbc.update("""
                INSERT INTO "IntentoAccesoEstudiante" ("idIntentoAcceso", "idEstudiante", "evento", "resultado", "origen", "fecha")
                VALUES (?, ?, ?, ?, ?, ?)
                """, UUID.randomUUID(), idEstudiante, evento, resultado, origenAcotado, marca(cuando));
    }

    @Override
    @Transactional
    public Purga purgar(OffsetDateTime ahora, int diasAuditoria) {
        int credenciales = jdbc.update("""
                DELETE FROM "CredencialAccesoEstudiante"
                WHERE LEAST(coalesce("fechaConsumo", 'infinity'), coalesce("fechaRevocacion", 'infinity'), "fechaExpiracion") < ?
                """, marca(ahora.minusHours(24)));
        int sesiones = jdbc.update("""
                DELETE FROM "SesionEstudiante"
                WHERE LEAST(coalesce("fechaRevocacion", 'infinity'), "fechaExpiracion",
                  "fechaUltimaActividad" + interval '30 minutes') < ?
                """, marca(ahora.minusDays(30)));
        int auditoria = jdbc.update("DELETE FROM \"IntentoAccesoEstudiante\" WHERE \"fecha\" < ?",
                marca(ahora.minusDays(diasAuditoria)));
        return new Purga(credenciales, sesiones, auditoria);
    }

    @Override
    public void revocarPorFalloDeEntrega(String challengeId) {
        UUID id;
        try {
            id = UUID.fromString(challengeId);
        } catch (IllegalArgumentException ex) {
            return;
        }
        jdbc.update("""
                UPDATE "CredencialAccesoEstudiante" SET "fechaRevocacion" = GREATEST(now(), "fechaCreacion")
                WHERE "challengeId" = ? AND "fechaConsumo" IS NULL AND "fechaRevocacion" IS NULL
                """, id);
    }

    private static VinculoEstudiante mapVinculo(ResultSet rs) throws SQLException {
        return new VinculoEstudiante(rs.getObject("idUsuario", UUID.class), rs.getObject("idEstudiante", UUID.class),
                rs.getString("correo"), "Activo".equalsIgnoreCase(rs.getString("estado")));
    }

    private static Timestamp marca(OffsetDateTime t) {
        return Timestamp.from(t.toInstant());
    }

    private static OffsetDateTime tiempo(ResultSet rs, String columna) throws SQLException {
        return rs.getTimestamp(columna).toInstant().atOffset(ZoneOffset.UTC);
    }
}
