package com.prisaly.config.dc92;

import com.prisaly.m1.application.port.IdentityAccessPort;
import com.prisaly.m4.application.service.SesionAcademicaService;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * DC-92 DD-09 («consulta docente limitada a sus propias sesiones y estudiantes convocados») and DD-13 («ausencias con
 * estados diferenciados, sin cero automático; la clasificación es del docente; trazabilidad de los cambios»).
 *
 * <p>Isolation is M4's existing session rule, reused unchanged: {@link SesionAcademicaService#consultarSesionAutorizada}
 * (authenticated personnel with {@code GESTIONAR_SESION} who programmed the session; 401/403/404 as M4). The rows are
 * {@link VistaParticipacionDc92}: only convoked students of that session, grades only when published.
 */
public final class ParticipacionesDocenteDc92 {
    private static final Logger auditoria = LoggerFactory.getLogger("prisaly.auditoria.ausencias");
    static final Set<String> CLASIFICACIONES = Set.of("AUSENCIA_JUSTIFICADA", "AUSENCIA_NO_JUSTIFICADA");
    /** DD-13: only an absence (pending review or already classified) can be (re)classified. */
    static final Set<String> CLASIFICABLES = Set.of("AUSENTE_PENDIENTE_REVISION", "AUSENCIA_JUSTIFICADA",
            "AUSENCIA_NO_JUSTIFICADA");

    public static final class RechazoDc92 extends RuntimeException {
        private static final long serialVersionUID = 1L;
        private final int estado;
        private final String codigo;
        public RechazoDc92(int estado, String codigo, String mensaje) {
            super(mensaje);
            this.estado = estado;
            this.codigo = codigo;
        }
        public int estado() { return estado; }
        public String codigo() { return codigo; }
    }

    public record Clasificacion(UUID idClasificacion, UUID idConvocatoria, String estado, UUID idDocente, String motivo,
            Instant fechaRegistro) { }

    private final SesionAcademicaService sesiones;
    private final IdentityAccessPort identidad;
    private final JdbcTemplate jdbc;
    private final VistaParticipacionDc92 vista;
    private final TransactionTemplate transaccion;

    ParticipacionesDocenteDc92(SesionAcademicaService sesiones, IdentityAccessPort identidad, JdbcTemplate jdbc,
            TransactionTemplate transaccion) {
        this.sesiones = Objects.requireNonNull(sesiones);
        this.identidad = Objects.requireNonNull(identidad);
        this.jdbc = Objects.requireNonNull(jdbc);
        this.vista = new VistaParticipacionDc92(jdbc);
        this.transaccion = Objects.requireNonNull(transaccion);
    }

    /** DD-09. */
    public List<VistaParticipacionDc92.Fila> participaciones(UUID idSesion) {
        var sesion = sesiones.consultarSesionAutorizada(idSesion);
        return vista.filas(sesion.getId());
    }

    /** DD-13: teacher classification, appended to the immutable history. */
    public Clasificacion clasificarAusencia(UUID idConvocatoria, String estado, String motivo) {
        if (estado == null || !CLASIFICACIONES.contains(estado)) {
            throw new RechazoDc92(400, "VALIDACION", "estado debe ser AUSENCIA_JUSTIFICADA o AUSENCIA_NO_JUSTIFICADA");
        }
        String nota = motivo == null || motivo.isBlank() ? null : motivo.strip();
        if (nota != null && nota.length() > 2000) {
            throw new RechazoDc92(400, "VALIDACION", "El motivo supera 2000 caracteres");
        }
        UUID sesion = sesionDe(idConvocatoria);
        sesiones.consultarSesionAutorizada(sesion);   // owner rule of M4 before any write
        UUID docente = identidad.currentUser().userId();
        Clasificacion c = transaccion.execute(tx -> {
            jdbc.queryForList("SELECT 1 FROM \"Convocatoria\" WHERE \"idConvocatoria\"=? FOR UPDATE", Integer.class, idConvocatoria);
            String actual = vista.filas(sesion).stream().filter(f -> f.idConvocatoria().equals(idConvocatoria))
                    .findFirst().map(VistaParticipacionDc92.Fila::estadoParticipacion).orElseThrow();
            if (!CLASIFICABLES.contains(actual) || actual.equals(estado)) {
                throw new RechazoDc92(409, "TRANSICION_NO_VALIDA", "No se puede pasar de " + actual + " a " + estado);
            }
            UUID id = UUID.randomUUID();
            jdbc.update("INSERT INTO \"ClasificacionAusencia\" (\"idClasificacion\",\"idConvocatoria\",\"estado\",\"idDocente\","
                    + "\"motivo\") VALUES (?,?,?,?,?)", id, idConvocatoria, estado, docente, nota);
            return leer(id);
        });
        auditoria.info("AUDITORIA_AUSENCIA convocatoria={} estado={} docente={}", idConvocatoria, estado, docente);
        return c;
    }

    /** DD-13 traceability: every classification, oldest first. */
    public List<Clasificacion> historialAusencia(UUID idConvocatoria) {
        sesiones.consultarSesionAutorizada(sesionDe(idConvocatoria));
        return jdbc.query("SELECT * FROM \"ClasificacionAusencia\" WHERE \"idConvocatoria\"=? ORDER BY \"fechaRegistro\"",
                (rs, n) -> new Clasificacion(rs.getObject("idClasificacion", UUID.class), idConvocatoria,
                        rs.getString("estado"), rs.getObject("idDocente", UUID.class), rs.getString("motivo"),
                        rs.getObject("fechaRegistro", OffsetDateTime.class).toInstant()), idConvocatoria);
    }

    UUID sesionDe(UUID idConvocatoria) {
        return jdbc.queryForList("SELECT \"idSesion\" FROM \"Convocatoria\" WHERE \"idConvocatoria\"=?", UUID.class,
                idConvocatoria).stream().findFirst().orElseThrow(() -> new RechazoDc92(404,
                "CONVOCATORIA_NO_ENCONTRADA", "Convocatoria no encontrada"));
    }

    private Clasificacion leer(UUID id) {
        return jdbc.queryForObject("SELECT * FROM \"ClasificacionAusencia\" WHERE \"idClasificacion\"=?",
                (rs, n) -> new Clasificacion(id, rs.getObject("idConvocatoria", UUID.class), rs.getString("estado"),
                        rs.getObject("idDocente", UUID.class), rs.getString("motivo"),
                        rs.getObject("fechaRegistro", OffsetDateTime.class).toInstant()), id);
    }
}
