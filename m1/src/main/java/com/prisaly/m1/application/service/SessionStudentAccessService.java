package com.prisaly.m1.application.service;

import com.prisaly.m1.api.IdentityDomainException;
import com.prisaly.m1.application.port.AccesoEstudiantePort;
import com.prisaly.m1.application.port.AccesoEstudiantePort.SesionGuardada;
import com.prisaly.m1.application.port.AccesoEstudiantePort.VinculoEstudiante;
import com.prisaly.m1.application.port.AuthenticatedStudent;
import com.prisaly.m1.application.port.StudentAccessPort;
import com.prisaly.m1.application.port.TokenSesionEstudianteActual;
import com.prisaly.m1.domain.student.ClavesAccesoEstudiante;
import com.prisaly.m1.domain.student.PoliticaCodigoAcceso;
import java.time.Clock;
import java.time.OffsetDateTime;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

/**
 * Identidad estudiantil desde la sesión propia. No lee el Bearer de personal ni el cuerpo HTTP.
 * DD-02 B (DC-DD02-B4-B5-01): the session token is verified with K-SES (current and, during an ordinary
 * rotation, previous versions).
 */
@Service
@ConditionalOnProperty(name = "prisaly.m1.student-access.enabled", havingValue = "true")
public class SessionStudentAccessService implements StudentAccessPort {

    private final AccesoEstudiantePort accesos;
    private final TokenSesionEstudianteActual tokenActual;
    private final ClavesAccesoEstudiante claves;
    private final Clock reloj;

    @Autowired
    public SessionStudentAccessService(AccesoEstudiantePort accesos, TokenSesionEstudianteActual tokenActual,
            ClavesAccesoEstudiante claves) {
        this(accesos, tokenActual, claves, Clock.systemUTC());
    }

    public SessionStudentAccessService(AccesoEstudiantePort accesos, TokenSesionEstudianteActual tokenActual,
            ClavesAccesoEstudiante claves, Clock reloj) {
        this.accesos = accesos;
        this.tokenActual = tokenActual;
        this.claves = claves;
        this.reloj = reloj;
    }

    @Override
    public AuthenticatedStudent requireCurrentStudent() {
        String token = tokenActual.actual().filter(valor -> !valor.isBlank()).orElseThrow(this::ausente);
        SesionGuardada sesion = accesos
                .sesionVigente(claves.sesion().firmasCandidatas(PoliticaCodigoAcceso.mensajeSesion(token)),
                        OffsetDateTime.now(reloj))
                .orElseThrow(this::ausente);
        VinculoEstudiante vinculo = accesos.vinculo(sesion.userId()).orElseThrow(this::ausente);
        if (!vinculo.cuentaActiva()) {
            throw new IdentityDomainException(IdentityAccessService.CODE_USER_INACTIVE, "User account is inactive");
        }
        if (vinculo.studentId() == null || vinculo.userId() == null || !vinculo.studentId().equals(sesion.studentId())
                || !vinculo.userId().equals(sesion.userId())) {
            throw ausente();
        }
        return new AuthenticatedStudent(vinculo.userId(), vinculo.studentId());
    }

    private IdentityDomainException ausente() {
        return new IdentityDomainException(DenyingStudentAccessService.CODE_STUDENT_IDENTITY_UNAVAILABLE,
                "Student identity is unavailable");
    }
}
