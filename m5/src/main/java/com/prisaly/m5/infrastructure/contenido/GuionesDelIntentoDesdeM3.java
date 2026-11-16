package com.prisaly.m5.infrastructure.contenido;

import com.prisaly.m3.api.GuionNarrativoEstudiantilPort;
import com.prisaly.m4.api.ConsultaProgramacionNoDisponibleException;
import com.prisaly.m4.api.ProgramacionParticipacionPort;
import com.prisaly.m5.application.ReferenciaConvocatoria;
import com.prisaly.m5.application.acceso.EstudianteNoIdentificadoException;
import com.prisaly.m5.application.acceso.IdentidadEstudianteActual;
import com.prisaly.m5.application.port.GuionesDelIntento;

import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * Adaptador de {@link GuionesDelIntento} (DC-BE-SIM-B1-B2A-24). Reutiliza el contrato de M3 hacia M5
 * ({@link GuionNarrativoEstudiantilPort}, Incremento A) y resuelve el caso de la convocatoria con el contrato de M4
 * para el estudiante autenticado, igual que la lectura de contenido: sin SQL entre módulos. Ambos puertos exigen la
 * transacción del llamador. No es un bean.
 */
public final class GuionesDelIntentoDesdeM3 implements GuionesDelIntento {

    private final GuionNarrativoEstudiantilPort guiones;
    private final ProgramacionParticipacionPort programacion;
    private final IdentidadEstudianteActual identidad;

    public GuionesDelIntentoDesdeM3(GuionNarrativoEstudiantilPort guiones, ProgramacionParticipacionPort programacion,
            IdentidadEstudianteActual identidad) {
        this.guiones = Objects.requireNonNull(guiones, "guiones");
        this.programacion = Objects.requireNonNull(programacion, "programacion");
        this.identidad = Objects.requireNonNull(identidad, "identidad");
    }

    @Override
    public Optional<UUID> publicadoParaConvocatoria(ReferenciaConvocatoria convocatoria) {
        var estudiante = identidad.actual().orElseThrow(EstudianteNoIdentificadoException::new);
        UUID convocatoriaId;
        UUID estudianteId;
        try {
            convocatoriaId = UUID.fromString(convocatoria.valor());
            estudianteId = UUID.fromString(estudiante.valor());
        } catch (IllegalArgumentException ex) {
            throw new ConsultaProgramacionNoDisponibleException(ex);
        }
        // El inicio ya verificó la titularidad en esta transacción: un vacío aquí es una inconsistencia, no un «sin guion».
        var contexto = programacion.consultarConvocatoria(convocatoriaId, estudianteId)
                .orElseThrow(ConsultaProgramacionNoDisponibleException::new);
        if (!convocatoriaId.equals(contexto.convocatoriaId()) || !estudianteId.equals(contexto.estudianteId())) {
            throw new ConsultaProgramacionNoDisponibleException();
        }
        return guiones.publicadoDelCaso(contexto.casoId()).map(GuionNarrativoEstudiantilPort.GuionPublicado::idGuion);
    }

    @Override
    public Optional<VersionGuion> version(UUID idGuion) {
        return guiones.publicado(idGuion).map(g -> new VersionGuion(g.idGuion(), g.version(), g.hashDefinicion(),
                g.definicionJson()));
    }
}
