package com.prisaly.m5.infrastructure.identidad;

import java.util.Optional;

import org.springframework.stereotype.Component;

import com.prisaly.m1.application.port.AuthenticatedStudent;
import com.prisaly.m1.application.port.StudentAccessPort;
import com.prisaly.m1.domain.rule.IdentityDomainException;
import com.prisaly.m5.application.acceso.IdentidadEstudianteActual;
import com.prisaly.m5.application.acceso.IdentificadorEstudiante;

/**
 * Adaptador de {@link IdentidadEstudianteActual} sobre el contrato publicado
 * por M1, {@link StudentAccessPort} (docs/M1-M5-STUDENT-IDENTITY-CONTRACT.md).
 *
 * <ul>
 *   <li>La identidad sale siempre del servidor: no recibe parámetros y no lee
 *       datos del cliente ni el {@code SecurityContext}.</li>
 *   <li>Usa {@code studentId} ({@code Estudiante.idEstudiante}) para la
 *       titularidad, como indica M1, y nunca {@code userId}.</li>
 *   <li>Cualquier denegación de M1 ({@link IdentityDomainException}, incluida
 *       {@code STUDENT_IDENTITY_UNAVAILABLE} mientras DD-02 siga abierta)
 *       significa "sin estudiante": el control de acceso deniega.</li>
 *   <li>Otros fallos del proveedor se propagan: nunca conceden acceso.</li>
 * </ul>
 * Mientras M1 solo publique {@code DenyingStudentAccessService}, este
 * adaptador nunca entrega una identidad en producción.
 */
@Component
public class IdentidadEstudianteDesdeM1 implements IdentidadEstudianteActual {

    private final StudentAccessPort accesoEstudiantil;

    public IdentidadEstudianteDesdeM1(StudentAccessPort accesoEstudiantil) {
        this.accesoEstudiantil = accesoEstudiantil;
    }

    @Override
    public Optional<IdentificadorEstudiante> actual() {
        AuthenticatedStudent estudiante;
        try {
            estudiante = accesoEstudiantil.requireCurrentStudent();
        } catch (IdentityDomainException denegado) {
            return Optional.empty();
        }
        // Respuesta incompleta o inconsistente: un Estudiante siempre tiene Usuario (FK de BD-005.2).
        if (estudiante == null || estudiante.studentId() == null || estudiante.userId() == null) {
            return Optional.empty();
        }
        return Optional.of(new IdentificadorEstudiante(estudiante.studentId().toString()));
    }
}
