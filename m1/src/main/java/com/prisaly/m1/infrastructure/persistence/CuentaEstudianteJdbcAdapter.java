package com.prisaly.m1.infrastructure.persistence;

import com.prisaly.m1.application.port.CuentaEstudiantePort;
import com.prisaly.m1.domain.student.PoliticaSesionEstudiante;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Persistencia JDBC de la cuenta estudiantil: vínculo con Usuario, códigos de activación
 * y sesiones. Guarda HMAC, nunca el código ni el token en claro. La activación y el cambio
 * de contraseña ocurren en la misma transacción.
 */
@Repository
public class CuentaEstudianteJdbcAdapter implements CuentaEstudiantePort {

    private final JdbcTemplate jdbc;

    public CuentaEstudianteJdbcAdapter(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public Optional<VinculoEstudiante> vinculo(UUID idUsuario) {
        return jdbc.query(
                """
                SELECT e."idUsuario", e."idEstudiante", u."estado"
                FROM "Estudiante" e
                JOIN "Usuario" u ON u."idUsuario" = e."idUsuario"
                WHERE e."idUsuario" = ?
                """,
                (rs, row) -> mapVinculo(rs),
                idUsuario).stream().findFirst();
    }

    @Override
    public Optional<CredencialEstudiante> porCorreo(String correoNormalizado) {
        return jdbc.query(
                """
                SELECT u."idUsuario", u."passwordHash", u."estado"
                FROM "Usuario" u
                WHERE lower(u."correo") = ?
                """,
                (rs, row) -> new CredencialEstudiante(
                        rs.getObject("idUsuario", UUID.class),
                        rs.getString("passwordHash"),
                        "Activo".equalsIgnoreCase(rs.getString("estado"))),
                correoNormalizado).stream().findFirst();
    }

    @Override
    public void revocarActivacionesAbiertas(UUID idUsuario, OffsetDateTime cuando) {
        jdbc.update(
                """
                UPDATE "ActivacionCuentaEstudiante"
                SET "fechaRevocacion" = ?
                WHERE "idUsuario" = ? AND "fechaConsumo" IS NULL AND "fechaRevocacion" IS NULL
                """,
                Timestamp.from(cuando.toInstant()),
                idUsuario);
    }

    @Override
    public void guardarActivacion(ActivacionNueva activacion) {
        jdbc.update(
                """
                INSERT INTO "ActivacionCuentaEstudiante" (
                  "idActivacion", "idUsuario", "idEstudiante", "challengeId", "codigoHmac",
                  "fechaCreacion", "fechaExpiracion")
                VALUES (?, ?, ?, ?, ?, ?, ?)
                """,
                activacion.idActivacion(),
                activacion.userId(),
                activacion.studentId(),
                activacion.challengeId(),
                activacion.codigoHmac(),
                Timestamp.from(activacion.creada().toInstant()),
                Timestamp.from(activacion.expira().toInstant()));
    }

    @Override
    public Optional<ActivacionGuardada> buscarActivacion(UUID challengeId) {
        return jdbc.query(
                """
                SELECT "challengeId", "idUsuario", "idEstudiante", "codigoHmac", "fechaExpiracion",
                       "fechaConsumo", "fechaRevocacion", "intentosFallidos"
                FROM "ActivacionCuentaEstudiante"
                WHERE "challengeId" = ?
                """,
                (rs, row) -> new ActivacionGuardada(
                        rs.getObject("challengeId", UUID.class),
                        rs.getObject("idUsuario", UUID.class),
                        rs.getObject("idEstudiante", UUID.class),
                        rs.getBytes("codigoHmac"),
                        tiempo(rs, "fechaExpiracion"),
                        rs.getTimestamp("fechaConsumo") != null,
                        rs.getTimestamp("fechaRevocacion") != null,
                        rs.getInt("intentosFallidos")),
                challengeId).stream().findFirst();
    }

    @Override
    public void registrarIntentoFallido(UUID challengeId) {
        jdbc.update(
                """
                UPDATE "ActivacionCuentaEstudiante"
                SET "intentosFallidos" = LEAST("intentosFallidos" + 1, 5)
                WHERE "challengeId" = ? AND "fechaConsumo" IS NULL AND "fechaRevocacion" IS NULL
                """,
                challengeId);
    }

    @Override
    @Transactional
    public boolean consumirYActivar(UUID challengeId, String passwordHash, OffsetDateTime cuando) {
        Timestamp marca = Timestamp.from(cuando.toInstant());
        // Only an account that never completed activation (no password yet) can be activated:
        // a deactivated account keeps its hash and must not be re-enabled by a new code.
        int consumidas = jdbc.update(
                """
                UPDATE "ActivacionCuentaEstudiante" a
                SET "fechaConsumo" = ?
                WHERE a."challengeId" = ?
                  AND a."fechaConsumo" IS NULL
                  AND a."fechaRevocacion" IS NULL
                  AND a."fechaExpiracion" > ?
                  AND a."intentosFallidos" < 5
                  AND EXISTS (
                    SELECT 1 FROM "Usuario" u
                    WHERE u."idUsuario" = a."idUsuario"
                      AND u."passwordHash" IS NULL
                      AND upper(u."estado") <> 'ACTIVO')
                """,
                marca,
                challengeId,
                marca);
        if (consumidas != 1) {
            return false;
        }
        int cuentas = jdbc.update(
                """
                UPDATE "Usuario" u
                SET "passwordHash" = ?, "estado" = 'Activo', "fechaActualizacion" = ?
                WHERE u."idUsuario" = (
                  SELECT a."idUsuario" FROM "ActivacionCuentaEstudiante" a WHERE a."challengeId" = ?)
                  AND u."passwordHash" IS NULL
                  AND upper(u."estado") <> 'ACTIVO'
                """,
                passwordHash,
                marca,
                challengeId);
        if (cuentas != 1) {
            throw new IllegalStateException("La activación no pudo marcar la cuenta");
        }
        return true;
    }

    @Override
    public void abrirSesion(SesionNueva sesion) {
        jdbc.update(
                """
                INSERT INTO "SesionEstudiante" (
                  "idSesionEstudiante", "idUsuario", "idEstudiante", "sesionTokenHmac",
                  "fechaCreacion", "fechaExpiracion", "fechaUltimaActividad")
                VALUES (?, ?, ?, ?, ?, ?, ?)
                """,
                sesion.idSesion(),
                sesion.userId(),
                sesion.studentId(),
                sesion.tokenHmac(),
                Timestamp.from(sesion.creada().toInstant()),
                Timestamp.from(sesion.expira().toInstant()),
                Timestamp.from(sesion.creada().toInstant()));
    }

    // Validates and records activity in one statement (no check-then-act race). Runs in its own
    // transaction so callers inside read-only transactions can still resolve the identity.
    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public Optional<SesionGuardada> sesionVigente(byte[] tokenHmac, OffsetDateTime ahora) {
        Timestamp marca = Timestamp.from(ahora.toInstant());
        Timestamp actividadMinima = Timestamp.from(
                ahora.minus(PoliticaSesionEstudiante.INACTIVIDAD_MAXIMA).toInstant());
        return jdbc.query(
                """
                UPDATE "SesionEstudiante"
                SET "fechaUltimaActividad" = GREATEST("fechaUltimaActividad", ?)
                WHERE "sesionTokenHmac" = ?
                  AND "fechaRevocacion" IS NULL
                  AND "fechaExpiracion" > ?
                  AND "fechaUltimaActividad" > ?
                RETURNING "idUsuario", "idEstudiante"
                """,
                (rs, row) -> new SesionGuardada(
                        rs.getObject("idUsuario", UUID.class),
                        rs.getObject("idEstudiante", UUID.class)),
                marca,
                tokenHmac,
                marca,
                actividadMinima).stream().findFirst();
    }

    @Override
    public void revocarSesion(byte[] tokenHmac, OffsetDateTime cuando) {
        jdbc.update(
                """
                UPDATE "SesionEstudiante"
                SET "fechaRevocacion" = ?
                WHERE "sesionTokenHmac" = ? AND "fechaRevocacion" IS NULL
                """,
                Timestamp.from(cuando.toInstant()),
                tokenHmac);
    }

    private static VinculoEstudiante mapVinculo(ResultSet rs) throws SQLException {
        return new VinculoEstudiante(
                rs.getObject("idUsuario", UUID.class),
                rs.getObject("idEstudiante", UUID.class),
                "Activo".equalsIgnoreCase(rs.getString("estado")));
    }

    private static OffsetDateTime tiempo(ResultSet rs, String columna) throws SQLException {
        Timestamp marca = rs.getTimestamp(columna);
        return marca.toInstant().atOffset(ZoneOffset.UTC);
    }
}
