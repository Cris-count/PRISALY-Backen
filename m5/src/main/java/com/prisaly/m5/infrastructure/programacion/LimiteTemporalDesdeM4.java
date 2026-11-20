package com.prisaly.m5.infrastructure.programacion;

import com.prisaly.m4.api.ConsultaProgramacionNoDisponibleException;
import com.prisaly.m4.api.ContextoProgramacion;
import com.prisaly.m4.api.ProgramacionAutenticadaPort;
import com.prisaly.m5.application.ReferenciaConvocatoria;
import com.prisaly.m5.application.inicio.InicioNoAutorizadoException;
import com.prisaly.m5.application.inicio.LimiteTemporalInicio;

import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * <b>L1 (F3-04) y RF-22, ratificados</b> por Dirección Central (DC-BE-M5-CIERRE-IMPLEMENTACION-45). Hasta esa orden era
 * el candidato {@code LimiteIndividualCandidatoDesdeM4} (DC-CLAUDE-BE-CIERRE-07). Lo ensambla
 * {@code ConfiguracionParticipacion}.
 *
 * <p>{@code limiteTemporal = min(inicio + tiempoMaximoParticipacion, finPrevisto)}, con los dos datos del contrato
 * vigente de M4. {@code inicio} es el instante de PostgreSQL ({@code clock_timestamp()}) que el caso de uso persiste
 * como {@code "Intento"."inicio"}: el plazo se decide con el mismo instante que se graba, nunca con el reloj de Java
 * o de Angular.
 *
 * <p>RF-22 (ingreso tardío, sin tolerancias): se puede iniciar mientras {@code inicio < finPrevisto}. Como la duración
 * máxima es positiva ({@code ck_sesion_tiempo}), eso equivale a que el límite sea posterior al inicio
 * ({@code ck_intento_tiempos}). Si no lo es, el inicio se rechaza con 403 {@code INICIO_NO_AUTORIZADO}, sin escribir:
 * es una regla ratificada, no una decisión pendiente ({@code INICIO_NO_DISPONIBLE}). La reanudación no llega aquí:
 * el límite persistido nunca se recalcula ni se amplía.
 */
public final class LimiteTemporalDesdeM4 implements LimiteTemporalInicio {

    private final ProgramacionAutenticadaPort programacion;

    public LimiteTemporalDesdeM4(ProgramacionAutenticadaPort programacion) {
        this.programacion = Objects.requireNonNull(programacion, "programacion");
    }

    /**
     * @throws InicioNoAutorizadoException si {@code inicio} ya alcanzó el fin previsto de la sesión (RF-22)
     * @throws ConsultaProgramacionNoDisponibleException si M4 no devuelve el contexto que la elegibilidad acaba de leer
     */
    @Override
    public Optional<Instant> calcular(ReferenciaConvocatoria convocatoria, Instant inicio) {
        if (inicio == null) {
            return Optional.empty();
        }
        UUID id = ElegibilidadInicioDesdeM4.uuid(convocatoria).orElseThrow(ConsultaProgramacionNoDisponibleException::new);
        ContextoProgramacion contexto = programacion.consultarConvocatoria(id)
                .orElseThrow(ConsultaProgramacionNoDisponibleException::new);
        return Optional.of(limite(contexto, inicio));
    }

    static Instant limite(ContextoProgramacion c, Instant inicio) {
        Instant porDuracion = inicio.plus(c.tiempoMaximoParticipacion());
        Instant limite = porDuracion.isBefore(c.finPrevisto()) ? porDuracion : c.finPrevisto();
        if (!limite.isAfter(inicio)) {
            throw new InicioNoAutorizadoException();
        }
        return limite;
    }
}
