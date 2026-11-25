package com.prisaly.config.m6;

import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * RF-27 minimal CANDIDATE (DC-BE-CONGELACION-CANDIDATO-88, RF27-CONTRATO-MINIMO-88). Called by
 * {@link LiberacionCalificacionM6} on the FIRST N2 release only, inside the same transaction: registers one
 * {@code Aviso} (type {@code Calificacion}) and one {@code EnvioCorreo} in Pendiente for the titular student, using the
 * existing V001 tables and unique keys ({@code claveEvento}/{@code claveIdempotencia} =
 * {@code calificacion-liberada:<idCalificacion>}). Delivery stays with the existing M7 worker. The text never carries
 * the grade value nor any detail (DD-12); the student reads the grade through the authenticated N2 query.
 */
public final class NotificacionCalificacionM7 {
    public static final String TIPO = "Calificacion";
    public static final String PREFIJO_CLAVE = "calificacion-liberada:";
    static final String TEXTO = "Tu calificación de la sesión {nombreSesion} del caso {nombreCaso} ya está disponible. "
            + "Consúltala en PRISALY.";
    static final String ASUNTO = "Calificación disponible: {nombreSesion}";

    private final JdbcTemplate jdbc;

    NotificacionCalificacionM7(JdbcTemplate jdbc) {
        this.jdbc = Objects.requireNonNull(jdbc, "jdbc");
    }

    /** Fails (and therefore rolls back the release) if the titular cannot be resolved: no silent release. */
    void notificar(UUID idCalificacion, UUID idIntento) {
        Map<String, Object> d = jdbc.queryForMap("""
                SELECT u."idUsuario", u."correo", c."idConvocatoria", p."idParticipacion",
                       s."nombreEspacioAcademico" AS sesion, ca."nombre" AS caso
                FROM "Intento" i
                JOIN "Participacion" p ON p."idParticipacion" = i."idParticipacion"
                JOIN "Convocatoria" c ON c."idConvocatoria" = p."idConvocatoria"
                JOIN "Estudiante" e ON e."idEstudiante" = c."idEstudiante"
                JOIN "Usuario" u ON u."idUsuario" = e."idUsuario"
                JOIN "Sesion" s ON s."idSesion" = c."idSesion"
                JOIN "Caso" ca ON ca."idCaso" = s."idCaso"
                WHERE i."idIntento" = ?""", idIntento);
        String clave = PREFIJO_CLAVE + Objects.requireNonNull(idCalificacion, "idCalificacion");
        String contenido = componer(TEXTO, d);
        String asunto = componer(ASUNTO, d);
        asunto = asunto.length() <= 255 ? asunto : asunto.substring(0, 255);
        jdbc.update("""
                INSERT INTO "Aviso" ("idAviso","idUsuarioDestino","idParticipacion","tipo","contenido","claveEvento")
                VALUES (gen_random_uuid(),?,?,?,?,?) ON CONFLICT ("idUsuarioDestino","claveEvento") DO NOTHING""",
                d.get("idUsuario"), d.get("idParticipacion"), TIPO, contenido, clave);
        jdbc.update("""
                INSERT INTO "EnvioCorreo" ("idEnvioCorreo","idUsuarioDestino","idConvocatoria","correoDestino","asunto",
                  "contenido","claveIdempotencia")
                VALUES (gen_random_uuid(),?,?,?,?,?,?) ON CONFLICT ("claveIdempotencia") DO NOTHING""",
                d.get("idUsuario"), d.get("idConvocatoria"), d.get("correo"), asunto, contenido, clave);
    }

    private static String componer(String plantilla, Map<String, Object> d) {
        return plantilla.replace("{nombreSesion}", String.valueOf(d.get("sesion")))
                .replace("{nombreCaso}", String.valueOf(d.get("caso")));
    }
}
