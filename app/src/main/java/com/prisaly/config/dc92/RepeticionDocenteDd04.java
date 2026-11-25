package com.prisaly.config.dc92;

import com.prisaly.m1.application.port.IdentityAccessPort;
import com.prisaly.m3.api.AutoriaCasoPort;
import com.prisaly.m4.api.ResponsableConvocatoriaPort;
import com.prisaly.m5.application.repeticion.AutorizacionRepeticionPort.AutorizacionRegistrada;
import com.prisaly.m5.application.repeticion.AutorizarRepeticionServicio;
import com.prisaly.m5.application.repeticion.ParticipacionNoEncontradaException;
import com.prisaly.m5.application.repeticion.AutorizacionRepeticionDenegadaException;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * DC-92 DD-04 (ratificada): «una repetición excepcional por estudiante, autorizada por el docente, con justificación e
 * historial» y «selección docente del intento que tendrá validez académica». Reutiliza sin cambios
 * {@link AutorizarRepeticionServicio} (solo el docente creador del caso; una autorización vigente a la vez; consumo
 * único en BD V005/V007). Añade solo lo ratificado: justificación obligatoria, límite de UNA repetición, la selección
 * del intento válido con su historial (V021, de solo inserción) y la consulta del historial. Cada operación queda en
 * el log de auditoría con usuario, fecha, motivo y operación.
 */
public final class RepeticionDocenteDd04 {
    private static final Logger auditoria = LoggerFactory.getLogger("prisaly.auditoria.repeticion");
    static final int MOTIVO_MAXIMO = 2000;

    /** Rejections with the code the HTTP layer exposes. */
    public static final class RechazoRepeticion extends RuntimeException {
        private static final long serialVersionUID = 1L;
        private final String codigo;
        private final int estado;
        RechazoRepeticion(int estado, String codigo, String mensaje) {
            super(mensaje);
            this.estado = estado;
            this.codigo = codigo;
        }
        public String codigo() { return codigo; }
        public int estado() { return estado; }
    }

    public record Seleccion(UUID idSeleccion, UUID idParticipacion, UUID idIntento, UUID idDocente, String motivo,
            Instant fechaSeleccion) { }

    public record IntentoHistorial(UUID idIntento, int numeroIntento, String estado, Instant inicio, Instant fin,
            String motivoFinalizacion, UUID idAutorizacion) { }

    public record AutorizacionHistorial(UUID idAutorizacion, UUID idDocenteAutorizador, String motivo,
            Instant fechaAutorizacion, String estadoAutorizacion) { }

    public record Historial(UUID idParticipacion, List<IntentoHistorial> intentos, List<AutorizacionHistorial> autorizaciones,
            List<Seleccion> selecciones, UUID idIntentoValido) { }

    private final AutorizarRepeticionServicio autorizar;
    private final ResponsableConvocatoriaPort responsables;
    private final AutoriaCasoPort autoria;
    private final IdentityAccessPort identidad;
    private final JdbcTemplate jdbc;
    private final TransactionTemplate transaccion;

    RepeticionDocenteDd04(AutorizarRepeticionServicio autorizar, ResponsableConvocatoriaPort responsables,
            AutoriaCasoPort autoria, IdentityAccessPort identidad, JdbcTemplate jdbc, TransactionTemplate transaccion) {
        this.autorizar = Objects.requireNonNull(autorizar);
        this.responsables = Objects.requireNonNull(responsables);
        this.autoria = Objects.requireNonNull(autoria);
        this.identidad = Objects.requireNonNull(identidad);
        this.jdbc = Objects.requireNonNull(jdbc);
        this.transaccion = Objects.requireNonNull(transaccion);
    }

    /** DD-04 reglas 2–4 y 7. */
    public AutorizacionRegistrada autorizar(UUID idParticipacion, String motivo) {
        String justificacion = motivoObligatorio(motivo);
        UUID docente = identidad.currentUser().userId();
        AutorizacionRegistrada registrada = transaccion.execute(estado -> {
            // Ownership and locking are AutorizarRepeticionServicio's; the single-repetition limit is checked after
            // its lock (FOR UPDATE on the participation), inside the same transaction.
            exigirParticipacion(idParticipacion);
            int intentos = contarIntentos(idParticipacion);
            if (intentos >= 2) {
                exigirCreador(docente, idParticipacion);
                throw new RechazoRepeticion(409, "REPETICION_AGOTADA",
                        "La participacion ya uso su unica repeticion excepcional");
            }
            return autorizar.autorizar(docente, idParticipacion, justificacion, null);
        });
        auditoria.info("AUDITORIA_REPETICION operacion=AUTORIZAR participacion={} autorizacion={} docente={}",
                idParticipacion, registrada.idAutorizacion(), docente);
        return registrada;
    }

    /** DD-04 reglas 5–7: selección del intento con validez académica, con historial inmutable. */
    public Seleccion seleccionar(UUID idParticipacion, UUID idIntento, String motivo) {
        String justificacion = motivoObligatorio(motivo);
        if (idIntento == null) {
            throw new RechazoRepeticion(400, "VALIDACION", "idIntento es obligatorio");
        }
        UUID docente = identidad.currentUser().userId();
        Seleccion s = transaccion.execute(estado -> {
            exigirParticipacion(idParticipacion);
            exigirCreador(docente, idParticipacion);
            UUID id = UUID.randomUUID();
            try {
                jdbc.update("INSERT INTO \"SeleccionIntento\" (\"idSeleccion\",\"idParticipacion\",\"idIntento\",\"idDocente\","
                        + "\"motivo\") VALUES (?,?,?,?,?)", id, idParticipacion, idIntento, docente, justificacion);
            } catch (org.springframework.dao.DataIntegrityViolationException ex) {
                throw new RechazoRepeticion(422, "INTENTO_NO_SELECCIONABLE",
                        "Solo se selecciona un intento finalizado de esta participacion");
            }
            return jdbc.queryForObject("SELECT * FROM \"SeleccionIntento\" WHERE \"idSeleccion\"=?",
                    (rs, n) -> new Seleccion(id, idParticipacion, idIntento, docente, rs.getString("motivo"),
                            rs.getObject("fechaSeleccion", OffsetDateTime.class).toInstant()), id);
        });
        auditoria.info("AUDITORIA_REPETICION operacion=SELECCIONAR_INTENTO participacion={} intento={} docente={}",
                idParticipacion, idIntento, docente);
        return s;
    }

    /** Historial completo (intentos, autorizaciones, selecciones) para el docente creador del caso. */
    public Historial historial(UUID idParticipacion) {
        UUID docente = identidad.currentUser().userId();
        return transaccion.execute(estado -> {
            exigirParticipacion(idParticipacion);
            exigirCreador(docente, idParticipacion);
            List<IntentoHistorial> intentos = jdbc.query("SELECT * FROM \"Intento\" WHERE \"idParticipacion\"=?"
                    + " ORDER BY \"numeroIntento\"", (rs, n) -> new IntentoHistorial(rs.getObject("idIntento", UUID.class),
                    rs.getInt("numeroIntento"), rs.getString("estado"), instante(rs.getObject("inicio", OffsetDateTime.class)),
                    instante(rs.getObject("fin", OffsetDateTime.class)), rs.getString("motivoFinalizacion"),
                    rs.getObject("idAutorizacion", UUID.class)), idParticipacion);
            List<AutorizacionHistorial> autorizaciones = jdbc.query("SELECT * FROM \"AutorizacionRepeticion\" WHERE"
                    + " \"idParticipacion\"=? ORDER BY \"fechaAutorizacion\"", (rs, n) -> new AutorizacionHistorial(
                    rs.getObject("idAutorizacion", UUID.class), rs.getObject("idDocenteAutorizador", UUID.class),
                    rs.getString("motivo"), instante(rs.getObject("fechaAutorizacion", OffsetDateTime.class)),
                    rs.getString("estadoAutorizacion")), idParticipacion);
            List<Seleccion> selecciones = jdbc.query("SELECT * FROM \"SeleccionIntento\" WHERE \"idParticipacion\"=?"
                    + " ORDER BY \"fechaSeleccion\"", (rs, n) -> new Seleccion(rs.getObject("idSeleccion", UUID.class),
                    idParticipacion, rs.getObject("idIntento", UUID.class), rs.getObject("idDocente", UUID.class),
                    rs.getString("motivo"), instante(rs.getObject("fechaSeleccion", OffsetDateTime.class))), idParticipacion);
            UUID valido = selecciones.isEmpty() ? null : selecciones.get(selecciones.size() - 1).idIntento();
            return new Historial(idParticipacion, intentos, autorizaciones, selecciones, valido);
        });
    }

    private void exigirParticipacion(UUID idParticipacion) {
        if (idParticipacion == null || jdbc.queryForList("SELECT 1 FROM \"Participacion\" WHERE \"idParticipacion\"=?"
                + " FOR UPDATE", Integer.class, idParticipacion).isEmpty()) {
            throw new ParticipacionNoEncontradaException();
        }
    }

    /** The existing RF-25 rule (creator of the case), applied through the same M4/M3 ports. */
    private void exigirCreador(UUID docente, UUID idParticipacion) {
        UUID convocatoria = jdbc.queryForObject("SELECT \"idConvocatoria\" FROM \"Participacion\" WHERE \"idParticipacion\"=?",
                UUID.class, idParticipacion);
        UUID caso = responsables.consultar(convocatoria)
                .orElseThrow(com.prisaly.m4.api.ConsultaProgramacionNoDisponibleException::new).idCaso();
        UUID creador = autoria.docenteCreador(caso).orElseThrow(com.prisaly.m3.api.AutoriaCasoNoDisponibleException::new);
        if (!creador.equals(docente)) {
            throw new AutorizacionRepeticionDenegadaException();
        }
    }

    private int contarIntentos(UUID idParticipacion) {
        return jdbc.queryForObject("SELECT count(*) FROM \"Intento\" WHERE \"idParticipacion\"=?", Integer.class, idParticipacion);
    }

    static String motivoObligatorio(String motivo) {
        if (motivo == null || motivo.isBlank()) {
            throw new RechazoRepeticion(400, "VALIDACION", "La justificacion es obligatoria");
        }
        String m = motivo.strip();
        if (m.length() > MOTIVO_MAXIMO) {
            throw new RechazoRepeticion(400, "VALIDACION", "La justificacion supera " + MOTIVO_MAXIMO + " caracteres");
        }
        return m;
    }

    private static Instant instante(OffsetDateTime o) {
        return o == null ? null : o.toInstant();
    }

}
