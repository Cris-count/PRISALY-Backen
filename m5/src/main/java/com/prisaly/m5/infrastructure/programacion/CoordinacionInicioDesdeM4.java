package com.prisaly.m5.infrastructure.programacion;

import com.prisaly.m4.api.ConsultaProgramacionNoDisponibleException;
import com.prisaly.m4.api.ProgramacionInicioCoordinadoPort;
import com.prisaly.m5.application.ConvocatoriaNoEncontradaException;
import com.prisaly.m5.application.ReferenciaConvocatoria;
import com.prisaly.m5.application.acceso.EstudianteNoIdentificadoException;
import com.prisaly.m5.application.acceso.IdentidadEstudianteActual;

import java.util.Objects;
import java.util.UUID;
import java.util.function.Consumer;

/** Reserva M4 antes de cualquier bloqueo Participacion/Intento, en la transacción M5. */
public final class CoordinacionInicioDesdeM4 implements Consumer<ReferenciaConvocatoria> {
    private final ProgramacionInicioCoordinadoPort programacion;
    private final IdentidadEstudianteActual identidad;

    public CoordinacionInicioDesdeM4(
            ProgramacionInicioCoordinadoPort programacion, IdentidadEstudianteActual identidad) {
        this.programacion = Objects.requireNonNull(programacion);
        this.identidad = Objects.requireNonNull(identidad);
    }

    @Override
    public void accept(ReferenciaConvocatoria convocatoria) {
        var estudiante = identidad.actual().orElseThrow(EstudianteNoIdentificadoException::new);
        UUID convocatoriaId;
        UUID estudianteId;
        try {
            convocatoriaId = UUID.fromString(convocatoria.valor());
            estudianteId = UUID.fromString(estudiante.valor());
        } catch (IllegalArgumentException ex) {
            throw new ConsultaProgramacionNoDisponibleException(ex);
        }
        var contexto = programacion.bloquearYConsultarConvocatoria(convocatoriaId, estudianteId);
        if (contexto == null) throw new ConsultaProgramacionNoDisponibleException();
        if (contexto.isEmpty()) throw new ConvocatoriaNoEncontradaException();
        if (!convocatoriaId.equals(contexto.get().convocatoriaId())
                || !estudianteId.equals(contexto.get().estudianteId())) {
            throw new ConsultaProgramacionNoDisponibleException();
        }
    }
}
