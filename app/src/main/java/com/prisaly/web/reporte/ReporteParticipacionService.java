package com.prisaly.web.reporte;

import com.prisaly.m4.application.service.SesionAcademicaService;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * DC-BE-CIERRE-FUNCIONAL-72 (RF-16 candidate): read-only participation report of ONE session for its owner teacher.
 *
 * <p>No policy is introduced: authorization is exactly M4's session read
 * ({@link SesionAcademicaService#consultarSesionAutorizada}: authenticated personnel, existing permission
 * {@code GESTIONAR_SESION}, owner of the session; 401/403/404 as M4). Only the official schema (V001–V009, V013) is
 * read; nothing is written. The {@code Calificacion} table is read only when M6's own switch
 * {@code prisaly.m6.evaluacion.enabled} is on, so this report never activates or bypasses M6 (N2 untouched).
 */
@Service
@ConditionalOnProperty(name = ReporteParticipacionController.PROPIEDAD, havingValue = "true")
public class ReporteParticipacionService {

    record Reporte(String nombreArchivo, byte[] contenido) { }

    private static final String SQL_BASE = """
            SELECT c."idEstudiante", u."nombre", u."correo", c."estadoAutorizacion", c."fechaRetiro",
                   p."estado" AS "estadoParticipacion",
                   (SELECT count(*) FROM "Intento" i WHERE i."idParticipacion" = p."idParticipacion") AS "intentos",
                   (SELECT count(*) FROM "Intento" i WHERE i."idParticipacion" = p."idParticipacion"
                       AND i."estado" = 'Finalizado') AS "finalizados",
                   ui."numeroIntento" AS "ultimoNumero", ui."estado" AS "ultimoEstado", ui."inicio" AS "ultimoInicio",
                   ui."fin" AS "ultimoFin", ui."motivoFinalizacion" AS "ultimoMotivo",
                   %s
            FROM "Convocatoria" c
            LEFT JOIN "Estudiante" e ON e."idEstudiante" = c."idEstudiante"
            LEFT JOIN "Usuario" u ON u."idUsuario" = e."idUsuario"
            LEFT JOIN "Participacion" p ON p."idConvocatoria" = c."idConvocatoria"
            LEFT JOIN LATERAL (SELECT i."numeroIntento", i."estado", i."inicio", i."fin", i."motivoFinalizacion"
                               FROM "Intento" i WHERE i."idParticipacion" = p."idParticipacion"
                               ORDER BY i."numeroIntento" DESC LIMIT 1) ui ON true
            %s
            WHERE c."idSesion" = ?
            ORDER BY u."nombre" NULLS LAST, c."idEstudiante"
            """;

    /** DD-04: the grade of the last authorized and evaluated attempt. */
    private static final String SQL_CALIFICACION = String.format(SQL_BASE,
            "cal.\"valor\" AS \"calificacion\", cal.\"numeroIntento\" AS \"intentoCalificado\"",
            """
            LEFT JOIN LATERAL (SELECT k."valor", i."numeroIntento"
                               FROM "Intento" i JOIN "Calificacion" k ON k."idIntento" = i."idIntento"
                               WHERE i."idParticipacion" = p."idParticipacion"
                               ORDER BY i."numeroIntento" DESC LIMIT 1) cal ON true
            """);

    private static final String SQL_SIN_CALIFICACION = String.format(SQL_BASE,
            "NULL::numeric AS \"calificacion\", NULL::int AS \"intentoCalificado\"", "");

    private final SesionAcademicaService sesiones;
    private final JdbcTemplate jdbc;
    private final boolean calificacionHabilitada;

    public ReporteParticipacionService(SesionAcademicaService sesiones, JdbcTemplate jdbc,
            @Value("${prisaly.m6.evaluacion.enabled:false}") boolean calificacionHabilitada) {
        this.sesiones = Objects.requireNonNull(sesiones, "sesiones");
        this.jdbc = Objects.requireNonNull(jdbc, "jdbc");
        this.calificacionHabilitada = calificacionHabilitada;
    }

    @Transactional(readOnly = true)
    public Reporte generar(UUID idSesion) {
        var sesion = sesiones.consultarSesionAutorizada(idSesion);
        var cabecera = new ReporteParticipacionCsv.Sesion(sesion.getId(), sesion.getTitulo(), sesion.getEstado());
        List<ReporteParticipacionCsv.Fila> filas = jdbc.query(
                calificacionHabilitada ? SQL_CALIFICACION : SQL_SIN_CALIFICACION,
                (rs, n) -> fila(rs), sesion.getId());
        return new Reporte("participacion-sesion-" + sesion.getId() + ".csv",
                ReporteParticipacionCsv.generar(cabecera, filas, calificacionHabilitada));
    }

    private static ReporteParticipacionCsv.Fila fila(ResultSet rs) throws SQLException {
        return new ReporteParticipacionCsv.Fila(rs.getObject("idEstudiante", UUID.class), rs.getString("nombre"),
                rs.getString("correo"), rs.getString("estadoAutorizacion"), instante(rs, "fechaRetiro"),
                rs.getString("estadoParticipacion"), rs.getInt("intentos"), rs.getInt("finalizados"),
                rs.getObject("ultimoNumero", Integer.class), rs.getString("ultimoEstado"), instante(rs, "ultimoInicio"),
                instante(rs, "ultimoFin"), rs.getString("ultimoMotivo"), rs.getBigDecimal("calificacion"),
                rs.getObject("intentoCalificado", Integer.class));
    }

    private static Instant instante(ResultSet rs, String columna) throws SQLException {
        var valor = rs.getObject(columna, OffsetDateTime.class);
        return valor == null ? null : valor.toInstant();
    }
}
