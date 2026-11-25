package com.prisaly.config.m6;

import com.prisaly.m1.application.port.IdentityAccessPort;
import com.prisaly.m6.application.AccesoEvaluacionDenegadoException;
import com.prisaly.m6.application.port.ConsultaRetroalimentacionPort;
import com.prisaly.m6.domain.model.RetroalimentacionGeneral;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * DC-92 DD-12 (with RF-26): the owner teacher registers the general feedback of a participation (existing table
 * {@code RetroalimentacionGeneral}, one per participation). Ownership is M6's rule ({@link EvaluacionDocenteM6}), the
 * same that evaluates and releases. The student sees it ONLY together with the released grade (N2), which is the
 * publication control of DD-12. No schema change.
 */
public final class RetroalimentacionDocenteDd12 {
    private static final Logger auditoria = LoggerFactory.getLogger("prisaly.auditoria.retroalimentacion");
    static final int MAXIMO = 5000;

    public static final class RechazoRetroalimentacion extends RuntimeException {
        private static final long serialVersionUID = 1L;
        private final int estado;
        private final String codigo;
        RechazoRetroalimentacion(int estado, String codigo, String mensaje) {
            super(mensaje);
            this.estado = estado;
            this.codigo = codigo;
        }
        public int estado() { return estado; }
        public String codigo() { return codigo; }
    }

    private final EvaluacionDocenteM6 evaluacion;
    private final IdentityAccessPort identidad;
    private final ConsultaRetroalimentacionPort lectura;
    private final JdbcTemplate jdbc;

    RetroalimentacionDocenteDd12(EvaluacionDocenteM6 evaluacion, IdentityAccessPort identidad,
            ConsultaRetroalimentacionPort lectura, JdbcTemplate jdbc) {
        this.evaluacion = Objects.requireNonNull(evaluacion);
        this.identidad = Objects.requireNonNull(identidad);
        this.lectura = Objects.requireNonNull(lectura);
        this.jdbc = Objects.requireNonNull(jdbc);
    }

    /** POST: first registration (409 if it already exists). */
    public RetroalimentacionGeneral registrar(UUID idParticipacion, String contenido) {
        String texto = contenido(contenido);
        UUID docente = exigirPropietario(idParticipacion);
        int n = jdbc.update("INSERT INTO \"RetroalimentacionGeneral\" (\"idRetroalimentacion\",\"idParticipacion\",\"idDocente\","
                + "\"contenido\") VALUES (gen_random_uuid(),?,?,?) ON CONFLICT (\"idParticipacion\") DO NOTHING",
                idParticipacion, docente, texto);
        if (n == 0) {
            throw new RechazoRetroalimentacion(409, "RETROALIMENTACION_EXISTENTE",
                    "La participacion ya tiene retroalimentacion general; use PUT para actualizarla");
        }
        auditoria.info("AUDITORIA_RETROALIMENTACION operacion=REGISTRAR participacion={} docente={}", idParticipacion, docente);
        return lectura.findByParticipacionId(idParticipacion).orElseThrow();
    }

    /** PUT: replaces the content of an existing feedback (404 if none). */
    public RetroalimentacionGeneral actualizar(UUID idParticipacion, String contenido) {
        String texto = contenido(contenido);
        UUID docente = exigirPropietario(idParticipacion);
        int n = jdbc.update("UPDATE \"RetroalimentacionGeneral\" SET \"contenido\"=?, \"idDocente\"=?, \"fechaRegistro\"=clock_timestamp()"
                + " WHERE \"idParticipacion\"=?", texto, docente, idParticipacion);
        if (n == 0) {
            throw new RechazoRetroalimentacion(404, "RETROALIMENTACION_NO_ENCONTRADA", "No hay retroalimentacion registrada");
        }
        auditoria.info("AUDITORIA_RETROALIMENTACION operacion=ACTUALIZAR participacion={} docente={}", idParticipacion, docente);
        return lectura.findByParticipacionId(idParticipacion).orElseThrow();
    }

    public RetroalimentacionGeneral consultar(UUID idParticipacion) {
        exigirPropietario(idParticipacion);
        return lectura.findByParticipacionId(idParticipacion).orElseThrow(() -> new RechazoRetroalimentacion(404,
                "RETROALIMENTACION_NO_ENCONTRADA", "No hay retroalimentacion registrada"));
    }

    /** M6 ownership through any attempt of the participation; a participation without attempts has nothing to assess. */
    private UUID exigirPropietario(UUID idParticipacion) {
        List<UUID> intentos = jdbc.queryForList("SELECT \"idIntento\" FROM \"Intento\" WHERE \"idParticipacion\"=?"
                + " ORDER BY \"numeroIntento\"", UUID.class, idParticipacion);
        if (intentos.isEmpty()) {
            throw new RechazoRetroalimentacion(404, "PARTICIPACION_NO_ENCONTRADA", "Participacion sin intentos o inexistente");
        }
        if (!evaluacion.esDelDocenteActual(intentos.get(0))) {
            throw new AccesoEvaluacionDenegadoException();
        }
        return identidad.currentUser().userId();
    }

    private static String contenido(String contenido) {
        if (contenido == null || contenido.isBlank()) {
            throw new RechazoRetroalimentacion(400, "VALIDACION", "contenido es obligatorio");
        }
        String t = contenido.strip();
        if (t.length() > MAXIMO) {
            throw new RechazoRetroalimentacion(400, "VALIDACION", "contenido supera " + MAXIMO + " caracteres");
        }
        return t;
    }
}
