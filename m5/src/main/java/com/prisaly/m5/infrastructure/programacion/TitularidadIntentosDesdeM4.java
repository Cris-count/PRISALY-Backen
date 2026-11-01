package com.prisaly.m5.infrastructure.programacion;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import com.prisaly.m4.api.ContextoProgramacion;
import com.prisaly.m4.api.ProgramacionParticipacionPort;
import com.prisaly.m5.application.ReferenciaIntento;
import com.prisaly.m5.application.acceso.IdentificadorEstudiante;
import com.prisaly.m5.application.acceso.Titularidad;
import com.prisaly.m5.application.acceso.TitularidadIntentos;
import com.prisaly.m5.infrastructure.persistencia.MapeoIntento;
import com.prisaly.m5.infrastructure.persistencia.PersistenciaIntentoException;

/**
 * Titularidad de un intento sobre el contrato público de M4.
 *
 * <ol>
 *   <li>M5 resuelve, en sus propias tablas, Intento."idParticipacion" →
 *       Participacion."idConvocatoria".</li>
 *   <li>M4 ({@link ProgramacionParticipacionPort#consultarConvocatoria}) dice si
 *       esa convocatoria es del estudiante. M5 no lee {@code "Convocatoria"}.</li>
 * </ol>
 *
 * <p>Falla de forma cerrada: un vacío de M4, o un contexto que no coincide con
 * la convocatoria y el estudiante consultados, es {@link Titularidad#AJENO}.
 * {@code ConsultaProgramacionNoDisponibleException} se propaga (503, sin la
 * causa). Un error de la consulta propia se envuelve en
 * {@link PersistenciaIntentoException} (500 genérico).
 *
 * <p>El adaptador por defecto de M4 lanza indisponibilidad salvo que se active
 * {@code prisaly.m4.consulta-bd0052.enabled=true}: sin esa propiedad, toda
 * verificación termina en 503 y nunca en acceso.
 */
@Component
public class TitularidadIntentosDesdeM4 implements TitularidadIntentos {

    static final String CONVOCATORIA_DEL_INTENTO = """
            SELECT p."idConvocatoria"
            FROM public."Intento" i
            JOIN public."Participacion" p ON p."idParticipacion" = i."idParticipacion"
            WHERE i."idIntento" = ?
            """;

    private final JdbcTemplate jdbc;
    private final ProgramacionParticipacionPort programacion;

    public TitularidadIntentosDesdeM4(JdbcTemplate jdbc, ProgramacionParticipacionPort programacion) {
        if (jdbc == null || programacion == null) {
            throw new IllegalArgumentException("La titularidad requiere la base y el contrato de M4");
        }
        this.jdbc = jdbc;
        this.programacion = programacion;
    }

    @Override
    public Titularidad de(ReferenciaIntento intento, IdentificadorEstudiante estudiante) {
        Optional<UUID> idIntento = MapeoIntento.idDe(intento);
        if (idIntento.isEmpty()) {
            return Titularidad.INEXISTENTE;
        }
        Optional<UUID> idEstudiante = uuid(estudiante.valor());
        if (idEstudiante.isEmpty()) {
            // Un identificador que no es Estudiante.idEstudiante no puede ser titular.
            return Titularidad.AJENO;
        }
        Optional<UUID> convocatoria = convocatoriaDe(idIntento.get());
        if (convocatoria.isEmpty()) {
            return Titularidad.INEXISTENTE;
        }
        Optional<ContextoProgramacion> contexto =
                programacion.consultarConvocatoria(convocatoria.get(), idEstudiante.get());
        boolean propio = contexto != null
                && contexto.filter(c -> convocatoria.get().equals(c.convocatoriaId())
                        && idEstudiante.get().equals(c.estudianteId())).isPresent();
        return propio ? Titularidad.PROPIO : Titularidad.AJENO;
    }

    private Optional<UUID> convocatoriaDe(UUID idIntento) {
        try {
            List<UUID> filas = jdbc.queryForList(CONVOCATORIA_DEL_INTENTO, UUID.class, idIntento);
            return filas.stream().findFirst();
        } catch (DataAccessException e) {
            throw new PersistenciaIntentoException("No se pudo resolver la participación del intento", e);
        }
    }

    private static Optional<UUID> uuid(String valor) {
        try {
            return Optional.of(UUID.fromString(valor));
        } catch (IllegalArgumentException e) {
            return Optional.empty();
        }
    }
}
