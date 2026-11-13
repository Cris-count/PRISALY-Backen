package com.prisaly.m5.infrastructure.programacion;

import com.prisaly.m4.api.ContextoProgramacion;
import com.prisaly.m4.api.ProgramacionAutenticadaPort;
import com.prisaly.m5.application.ReferenciaConvocatoria;
import com.prisaly.m5.application.inicio.LimiteTemporalInicio;

import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * <b>Candidato</b> de límite individual del intento (F3-04; DC-CLAUDE-BE-CIERRE-07, prioridad 3), <b>no ratificado</b>.
 * No es un bean y el ensamblado de producción sigue usando {@code LimiteTemporalNoRatificado}.
 *
 * <p>Con los dos datos que F3-04 nombra, ambos del contrato vigente de M4:
 * {@code limite = min(inicio + tiempoMaximoParticipacion, finPrevisto)}. {@code inicio} es el instante de
 * PostgreSQL que entrega el caso de uso. Si el resultado no es posterior a {@code inicio} ({@code ck_intento_tiempos})
 * o no hay contexto, devuelve vacío y no se crea ningún intento.
 *
 * <p>No aplica ingreso tardío (RF-22): no reduce ni amplía el límite según la hora de ingreso, ni usa
 * {@code tiempoEstimadoCaso}.
 */
public final class LimiteIndividualCandidatoDesdeM4 implements LimiteTemporalInicio {

    private final ProgramacionAutenticadaPort programacion;

    public LimiteIndividualCandidatoDesdeM4(ProgramacionAutenticadaPort programacion) {
        this.programacion = Objects.requireNonNull(programacion, "programacion");
    }

    @Override
    public Optional<Instant> calcular(ReferenciaConvocatoria convocatoria, Instant inicio) {
        if (inicio == null) {
            return Optional.empty();
        }
        Optional<UUID> id = ElegibilidadCandidataDesdeM4.uuid(convocatoria);
        if (id.isEmpty()) {
            return Optional.empty();
        }
        return programacion.consultarConvocatoria(id.get()).flatMap(c -> limite(c, inicio));
    }

    static Optional<Instant> limite(ContextoProgramacion c, Instant inicio) {
        Instant porDuracion = inicio.plus(c.tiempoMaximoParticipacion());
        Instant limite = porDuracion.isBefore(c.finPrevisto()) ? porDuracion : c.finPrevisto();
        return limite.isAfter(inicio) ? Optional.of(limite) : Optional.empty();
    }
}
