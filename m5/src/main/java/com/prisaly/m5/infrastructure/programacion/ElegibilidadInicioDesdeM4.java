package com.prisaly.m5.infrastructure.programacion;

import com.prisaly.m4.api.ContextoProgramacion;
import com.prisaly.m4.api.ProgramacionAutenticadaPort;
import com.prisaly.m5.application.ReferenciaConvocatoria;
import com.prisaly.m5.application.inicio.ElegibilidadInicio;

import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * <b>E1 — elegibilidad del primer inicio, ratificada</b> por Dirección Central (DC-BE-M5-CIERRE-IMPLEMENTACION-45).
 * Hasta esa orden era la candidata {@code ElegibilidadCandidataDesdeM4} (DC-CLAUDE-BE-CIERRE-07); la regla es la misma.
 * La ensambla {@code ConfiguracionParticipacion}.
 *
 * <p>Solo usa el contrato vigente de M4 ({@link ProgramacionAutenticadaPort}, que resuelve al estudiante por la
 * identidad de M1, así que nunca devuelve datos de otro estudiante) y datos persistidos:
 * <ul>
 *   <li>la sesión está {@code EnCurso} (abierta por el docente);</li>
 *   <li>la convocatoria es del estudiante, está {@code Autorizado} y no tiene {@code fechaRetiro};</li>
 *   <li>el caso está {@code Publicado} y activo (el contenido estudiantil de M3 no se sirve de un caso inactivo).</li>
 * </ul>
 * La identidad válida la exige antes el proxy de M5, y las restricciones de intentos (V003–V007) las garantiza
 * PostgreSQL al insertar. El plazo (RF-22) y el límite (L1) se deciden en {@link LimiteTemporalDesdeM4} con el
 * instante de inicio que se persiste. Sin contexto: {@code NO_ELEGIBLE} (403); un fallo técnico de M4 se propaga
 * (falla cerrado).
 */
public final class ElegibilidadInicioDesdeM4 implements ElegibilidadInicio {

    static final String SESION_EN_CURSO = "EnCurso";
    static final String CONVOCATORIA_AUTORIZADA = "Autorizado";

    private final ProgramacionAutenticadaPort programacion;

    public ElegibilidadInicioDesdeM4(ProgramacionAutenticadaPort programacion) {
        this.programacion = Objects.requireNonNull(programacion, "programacion");
    }

    @Override
    public Resultado evaluar(ReferenciaConvocatoria convocatoria) {
        Optional<UUID> id = uuid(convocatoria);
        if (id.isEmpty()) {
            return Resultado.NO_ELEGIBLE;
        }
        Optional<ContextoProgramacion> contexto = programacion.consultarConvocatoria(id.get());
        return contexto.filter(ElegibilidadInicioDesdeM4::cumple).isPresent()
                ? Resultado.ELEGIBLE : Resultado.NO_ELEGIBLE;
    }

    static boolean cumple(ContextoProgramacion c) {
        return SESION_EN_CURSO.equals(c.estadoSesion())
                && CONVOCATORIA_AUTORIZADA.equals(c.estadoAutorizacionConvocatoria())
                && c.fechaRetiroConvocatoria().isEmpty()
                && c.casoPublicadoYActivo();
    }

    static Optional<UUID> uuid(ReferenciaConvocatoria convocatoria) {
        if (convocatoria == null) {
            return Optional.empty();
        }
        try {
            return Optional.of(UUID.fromString(convocatoria.valor()));
        } catch (IllegalArgumentException ex) {
            return Optional.empty();
        }
    }
}
