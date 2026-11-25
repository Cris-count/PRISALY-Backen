package com.prisaly.config.dc92;

import com.prisaly.m1.application.port.IdentityAccessPort;
import com.prisaly.m4.application.service.SesionAcademicaService;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * DC-92 RF-21 (ratificada): «retiro con conservación del historial, bloqueo de futuras participaciones y
 * notificaciones». DC-90 rules implemented literally:
 * <ol>
 *   <li>date and person responsible ({@code RetiroConvocatoria}, V023, immutable);</li>
 *   <li>reason when given;</li>
 *   <li>no new participation: the convocation gets {@code fechaRetiro} + {@code estadoAutorizacion='Retirado'}, which
 *       M5's ratified eligibility (E1) already refuses;</li>
 *   <li>future convocations: the convocation row stays, so re-convoking the same student is M4's existing duplicate
 *       rejection;</li>
 *   <li>attempts, answers and grades are never deleted;</li>
 *   <li>an attempt in progress must be resolved EXPLICITLY: without {@code resolucionIntentoEnCurso=FINALIZAR_INTENTO}
 *       the withdrawal is refused (409); with it the attempt is finalized with reason {@code RETIRO};</li>
 *   <li>one notice + email for the student and one notice for the teacher (M7 tables, unique event keys; the email
 *       never carries the reason);</li>
 *   <li>audit log line.</li>
 * </ol>
 * Authorization is M4's existing session rule (programmer with {@code GESTIONAR_SESION}), the same that convokes.
 */
public final class RetiroConvocatoriaRf21 {
    private static final Logger auditoria = LoggerFactory.getLogger("prisaly.auditoria.retiro");
    public static final String FINALIZAR_INTENTO = "FINALIZAR_INTENTO";

    public record Retiro(UUID idConvocatoria, UUID idDocente, String motivo, String resolucionIntento,
            UUID idIntentoResuelto, Instant fechaRetiro) { }

    private final SesionAcademicaService sesiones;
    private final IdentityAccessPort identidad;
    private final JdbcTemplate jdbc;
    private final TransactionTemplate transaccion;
    private final ParticipacionesDocenteDc92 auxiliar;

    RetiroConvocatoriaRf21(SesionAcademicaService sesiones, IdentityAccessPort identidad, JdbcTemplate jdbc,
            TransactionTemplate transaccion) {
        this.sesiones = Objects.requireNonNull(sesiones);
        this.identidad = Objects.requireNonNull(identidad);
        this.jdbc = Objects.requireNonNull(jdbc);
        this.transaccion = Objects.requireNonNull(transaccion);
        this.auxiliar = new ParticipacionesDocenteDc92(sesiones, identidad, jdbc, transaccion);
    }

    public Retiro retirar(UUID idConvocatoria, String motivo, String resolucion) {
        String nota = motivo == null || motivo.isBlank() ? null : motivo.strip();
        if (nota != null && nota.length() > 2000) {
            throw new ParticipacionesDocenteDc92.RechazoDc92(400, "VALIDACION", "El motivo supera 2000 caracteres");
        }
        if (resolucion != null && !FINALIZAR_INTENTO.equals(resolucion)) {
            throw new ParticipacionesDocenteDc92.RechazoDc92(400, "VALIDACION",
                    "resolucionIntentoEnCurso solo admite " + FINALIZAR_INTENTO);
        }
        UUID sesion = auxiliar.sesionDe(idConvocatoria);
        sesiones.consultarSesionAutorizada(sesion);
        UUID docente = identidad.currentUser().userId();
        Retiro r = transaccion.execute(tx -> {
            Map<String, Object> c = jdbc.queryForMap("SELECT c.\"fechaRetiro\", c.\"idEstudiante\", p.\"idParticipacion\""
                    + " FROM \"Convocatoria\" c LEFT JOIN \"Participacion\" p ON p.\"idConvocatoria\" = c.\"idConvocatoria\""
                    + " WHERE c.\"idConvocatoria\"=? FOR UPDATE OF c", idConvocatoria);
            if (c.get("fechaRetiro") != null) {
                throw new ParticipacionesDocenteDc92.RechazoDc92(409, "YA_RETIRADO", "La convocatoria ya fue retirada");
            }
            UUID participacion = (UUID) c.get("idParticipacion");
            Optional<UUID> enCurso = participacion == null ? Optional.empty() : jdbc.queryForList(
                    "SELECT \"idIntento\" FROM \"Intento\" WHERE \"idParticipacion\"=? AND \"estado\"='EnCurso' FOR UPDATE",
                    UUID.class, participacion).stream().findFirst();
            String resolucionIntento = "SIN_INTENTO_EN_CURSO";
            if (enCurso.isPresent()) {
                if (!FINALIZAR_INTENTO.equals(resolucion)) {
                    throw new ParticipacionesDocenteDc92.RechazoDc92(409, "INTENTO_EN_CURSO_REQUIERE_RESOLUCION",
                            "Hay un intento en curso: indique resolucionIntentoEnCurso=" + FINALIZAR_INTENTO);
                }
                jdbc.update("UPDATE \"Intento\" SET \"estado\"='Finalizado', \"fin\"=clock_timestamp(),"
                        + " \"motivoFinalizacion\"='RETIRO' WHERE \"idIntento\"=? AND \"estado\"='EnCurso'", enCurso.get());
                resolucionIntento = "INTENTO_FINALIZADO_POR_RETIRO";
            }
            jdbc.update("UPDATE \"Convocatoria\" SET \"fechaRetiro\"=clock_timestamp(), \"estadoAutorizacion\"='Retirado'"
                    + " WHERE \"idConvocatoria\"=?", idConvocatoria);
            jdbc.update("INSERT INTO \"RetiroConvocatoria\" (\"idConvocatoria\",\"idDocente\",\"motivo\",\"resolucionIntento\","
                    + "\"idIntentoResuelto\") VALUES (?,?,?,?,?)", idConvocatoria, docente, nota, resolucionIntento,
                    enCurso.orElse(null));
            notificar(idConvocatoria, participacion, docente);
            return leer(idConvocatoria).orElseThrow();
        });
        auditoria.info("AUDITORIA_RETIRO convocatoria={} docente={} resolucion={} intento={}", idConvocatoria, docente,
                r.resolucionIntento(), r.idIntentoResuelto());
        return r;
    }

    public Optional<Retiro> consultar(UUID idConvocatoria) {
        sesiones.consultarSesionAutorizada(auxiliar.sesionDe(idConvocatoria));
        return leer(idConvocatoria);
    }

    private Optional<Retiro> leer(UUID idConvocatoria) {
        List<Retiro> r = jdbc.query("SELECT * FROM \"RetiroConvocatoria\" WHERE \"idConvocatoria\"=?", (rs, n) -> new Retiro(
                idConvocatoria, rs.getObject("idDocente", UUID.class), rs.getString("motivo"), rs.getString("resolucionIntento"),
                rs.getObject("idIntentoResuelto", UUID.class), rs.getObject("fechaRetiro", OffsetDateTime.class).toInstant()),
                idConvocatoria);
        return r.stream().findFirst();
    }

    /** One notice + email to the student, one notice to the teacher; event keys make them unique. */
    private void notificar(UUID idConvocatoria, UUID participacion, UUID docente) {
        Map<String, Object> d = jdbc.queryForMap("SELECT u.\"idUsuario\", u.\"correo\", u.\"nombre\" AS estudiante,"
                + " s.\"nombreEspacioAcademico\" AS sesion, ca.\"nombre\" AS caso FROM \"Convocatoria\" c"
                + " JOIN \"Estudiante\" e ON e.\"idEstudiante\" = c.\"idEstudiante\" JOIN \"Usuario\" u ON u.\"idUsuario\" = e.\"idUsuario\""
                + " JOIN \"Sesion\" s ON s.\"idSesion\" = c.\"idSesion\" JOIN \"Caso\" ca ON ca.\"idCaso\" = s.\"idCaso\""
                + " WHERE c.\"idConvocatoria\"=?", idConvocatoria);
        String textoEstudiante = "Has sido retirado de la sesión " + d.get("sesion") + " del caso " + d.get("caso")
                + ". Tu historial se conserva.";
        String clave = "retiro:" + idConvocatoria;
        jdbc.update("INSERT INTO \"Aviso\" (\"idAviso\",\"idUsuarioDestino\",\"idParticipacion\",\"tipo\",\"contenido\",\"claveEvento\")"
                + " VALUES (gen_random_uuid(),?,?,'Retiro',?,?) ON CONFLICT (\"idUsuarioDestino\",\"claveEvento\") DO NOTHING",
                d.get("idUsuario"), participacion, textoEstudiante, clave);
        jdbc.update("INSERT INTO \"EnvioCorreo\" (\"idEnvioCorreo\",\"idUsuarioDestino\",\"idConvocatoria\",\"correoDestino\","
                + "\"asunto\",\"contenido\",\"claveIdempotencia\") VALUES (gen_random_uuid(),?,?,?,?,?,?)"
                + " ON CONFLICT (\"claveIdempotencia\") DO NOTHING", d.get("idUsuario"), idConvocatoria, d.get("correo"),
                "Retiro de la sesión " + d.get("sesion"), textoEstudiante, clave);
        jdbc.update("INSERT INTO \"Aviso\" (\"idAviso\",\"idUsuarioDestino\",\"idParticipacion\",\"tipo\",\"contenido\",\"claveEvento\")"
                + " VALUES (gen_random_uuid(),?,?,'Retiro',?,?) ON CONFLICT (\"idUsuarioDestino\",\"claveEvento\") DO NOTHING",
                docente, participacion, "Se registró el retiro de " + d.get("estudiante") + " de la sesión " + d.get("sesion") + ".",
                "retiro-docente:" + idConvocatoria);
    }
}
