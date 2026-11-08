package com.prisaly.m1.application.service;

import com.prisaly.m1.application.port.AuthenticatedStudent;
import com.prisaly.m1.application.port.CuentaEstudiantePort;
import com.prisaly.m1.application.port.CuentaEstudiantePort.SesionGuardada;
import com.prisaly.m1.application.port.CuentaEstudiantePort.VinculoEstudiante;
import com.prisaly.m1.application.port.StudentAccessPort;
import com.prisaly.m1.application.port.TokenSesionEstudianteActual;
import com.prisaly.m1.domain.rule.IdentityDomainException;
import com.prisaly.m1.domain.student.ClaveHmacEstudiante;
import com.prisaly.m1.domain.student.FirmasEstudiante;
import java.time.Clock;
import java.time.OffsetDateTime;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

/**
 * Identidad estudiantil desde la sesión propia. No lee el Bearer de personal ni el cuerpo HTTP.
 */
@Service
@ConditionalOnProperty(name = "prisaly.m1.student-access.enabled", havingValue = "true")
public class SessionStudentAccessService implements StudentAccessPort {

    private final CuentaEstudiantePort cuentas;
    private final TokenSesionEstudianteActual tokenActual;
    private final ClaveHmacEstudiante clave;
    private final Clock reloj;

    @Autowired
    public SessionStudentAccessService(
            CuentaEstudiantePort cuentas,
            TokenSesionEstudianteActual tokenActual,
            ClaveHmacEstudiante clave) {
        this(cuentas, tokenActual, clave, Clock.systemUTC());
    }

    public SessionStudentAccessService(
            CuentaEstudiantePort cuentas,
            TokenSesionEstudianteActual tokenActual,
            ClaveHmacEstudiante clave,
            Clock reloj) {
        this.cuentas = cuentas;
        this.tokenActual = tokenActual;
        this.clave = clave;
        this.reloj = reloj;
    }

    @Override
    public AuthenticatedStudent requireCurrentStudent() {
        String token = tokenActual.actual().filter(valor -> !valor.isBlank()).orElseThrow(this::ausente);
        SesionGuardada sesion = cuentas
                .sesionVigente(
                        FirmasEstudiante.hmac(clave.valor(), "sesion:" + token),
                        OffsetDateTime.now(reloj))
                .orElseThrow(this::ausente);
        VinculoEstudiante vinculo = cuentas.vinculo(sesion.userId()).orElseThrow(this::ausente);
        if (!vinculo.cuentaActiva()) {
            throw new IdentityDomainException(
                    IdentityAccessService.CODE_USER_INACTIVE, "User account is inactive");
        }
        if (vinculo.studentId() == null
                || vinculo.userId() == null
                || !vinculo.studentId().equals(sesion.studentId())
                || !vinculo.userId().equals(sesion.userId())) {
            throw ausente();
        }
        return new AuthenticatedStudent(vinculo.userId(), vinculo.studentId());
    }

    private IdentityDomainException ausente() {
        return new IdentityDomainException(
                DenyingStudentAccessService.CODE_STUDENT_IDENTITY_UNAVAILABLE,
                "Student identity is unavailable");
    }
}
