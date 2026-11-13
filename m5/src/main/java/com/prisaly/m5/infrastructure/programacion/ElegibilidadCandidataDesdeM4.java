package com.prisaly.m5.infrastructure.programacion;

import com.prisaly.m4.api.ContextoProgramacion;
import com.prisaly.m4.api.ProgramacionAutenticadaPort;
import com.prisaly.m5.application.ReferenciaConvocatoria;
import com.prisaly.m5.application.inicio.ElegibilidadInicio;

import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * <b>Candidata</b> de elegibilidad de primer inicio (DC-CLAUDE-BE-CIERRE-07, prioridad 3), <b>no ratificada</b>.
 * No es un bean y el ensamblado de producción ({@code ConfiguracionParticipacion}) sigue usando
 * {@code ElegibilidadNoRatificada}: activarla exige ratificar la regla y cambiar ese ensamblado.
 *
 * <p>Solo usa el contrato vigente de M4 ({@link ProgramacionAutenticadaPort}, identidad de M1) y datos persistidos:
 * <ul>
 *   <li>la sesión está {@code EnCurso};</li>
 *   <li>la convocatoria está {@code Autorizado} y sin {@code fechaRetiro};</li>
 *   <li>el caso está {@code Publicado} y activo.</li>
 * </ul>
 * <b>No aplica ingreso tardío</b> (RF-22 sin ratificar): no compara el instante de inicio con
 * {@code fechaInicioReal}, {@code inicioPrevisto} ni {@code tiempoEstimadoCaso}. Si la consulta no devuelve
 * contexto, la respuesta es {@code NO_ELEGIBLE}; un fallo técnico de M4 se propaga (falla cerrado).
 */
public final class ElegibilidadCandidataDesdeM4 implements ElegibilidadInicio {

    static final String SESION_EN_CURSO = "EnCurso";
    static final String CONVOCATORIA_AUTORIZADA = "Autorizado";

    private final ProgramacionAutenticadaPort programacion;

    public ElegibilidadCandidataDesdeM4(ProgramacionAutenticadaPort programacion) {
        this.programacion = Objects.requireNonNull(programacion, "programacion");
    }

    @Override
    public Resultado evaluar(ReferenciaConvocatoria convocatoria) {
        Optional<UUID> id = uuid(convocatoria);
        if (id.isEmpty()) {
            return Resultado.NO_ELEGIBLE;
        }
        Optional<ContextoProgramacion> contexto = programacion.consultarConvocatoria(id.get());
        return contexto.filter(ElegibilidadCandidataDesdeM4::cumple).isPresent()
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
