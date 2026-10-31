package com.prisaly.m4.infrastructure.adapter;

import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

import com.prisaly.m1.application.port.StudentAccessPort;
import com.prisaly.m1.domain.rule.IdentityDomainException;
import com.prisaly.m4.api.ContextoProgramacion;
import com.prisaly.m4.api.ProgramacionAutenticadaPort;
import com.prisaly.m4.api.ProgramacionParticipacionPort;
import com.prisaly.m4.domain.rule.ReglaNegocioException;

public final class ProgramacionAutenticadaM1Adapter implements ProgramacionAutenticadaPort {
    private final StudentAccessPort identidad;
    private final ProgramacionParticipacionPort programacion;

    public ProgramacionAutenticadaM1Adapter(StudentAccessPort identidad, ProgramacionParticipacionPort programacion) {
        this.identidad = Objects.requireNonNull(identidad);
        this.programacion = Objects.requireNonNull(programacion);
    }

    @Override
    public Optional<ContextoProgramacion> consultarSesion(UUID sesionId) {
        return programacion.consultarSesion(Objects.requireNonNull(sesionId), estudianteActual());
    }

    @Override
    public Optional<ContextoProgramacion> consultarConvocatoria(UUID convocatoriaId) {
        return programacion.consultarConvocatoria(Objects.requireNonNull(convocatoriaId), estudianteActual());
    }

    private UUID estudianteActual() {
        try {
            var actual = identidad.requireCurrentStudent();
            if (actual == null || actual.studentId() == null || actual.userId() == null) {
                throw new ReglaNegocioException("M4-NO-AUTENTICADO", "No existe identidad estudiantil autenticada");
            }
            return actual.studentId();
        } catch (IdentityDomainException ex) {
            if ("UNAUTHENTICATED".equals(ex.code())) {
                throw new ReglaNegocioException("M4-NO-AUTENTICADO", "Se requiere autenticación estudiantil");
            }
            if ("USER_INACTIVE".equals(ex.code()) || "USER_NOT_FOUND".equals(ex.code())) {
                throw new ReglaNegocioException("M4-SIN-PERMISO", "La identidad estudiantil no está habilitada");
            }
            throw new ReglaNegocioException("M4-PROVEEDOR-NO-DISPONIBLE",
                    "M1 no dispone de un proveedor de identidad estudiantil habilitado");
        }
    }
}
