package com.prisaly.m1.application.service;

import com.prisaly.m1.application.port.AccesoEstudiantePort;
import com.prisaly.m1.application.port.AccesoEstudiantePort.Purga;
import java.time.Clock;
import java.time.OffsetDateTime;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

/**
 * D-B2.4 retention for M1 (credentials 24 h after closing, finished sessions 30 days, access audit N days).
 * Registered together with the student-access flag: the purge cannot be left permanently disabled while the access
 * is enabled (condition of DC-DD02-B2-02). The audit period (365 days proposed) is pending institutional validation.
 */
@Service
@ConditionalOnProperty(name = "prisaly.m1.student-access.enabled", havingValue = "true")
public class PurgaAccesoEstudianteService {

    private final AccesoEstudiantePort accesos;
    private final int diasAuditoria;
    private final Clock reloj;

    @Autowired
    public PurgaAccesoEstudianteService(AccesoEstudiantePort accesos,
            @Value("${prisaly.m1.student-access.retencion.auditoria-dias:365}") int diasAuditoria) {
        this(accesos, diasAuditoria, Clock.systemUTC());
    }

    public PurgaAccesoEstudianteService(AccesoEstudiantePort accesos, int diasAuditoria, Clock reloj) {
        if (diasAuditoria < 1) {
            throw new IllegalArgumentException("La retención de auditoría debe ser de al menos 1 día");
        }
        this.accesos = accesos;
        this.diasAuditoria = diasAuditoria;
        this.reloj = reloj;
    }

    public Purga purgar() {
        return accesos.purgar(OffsetDateTime.now(reloj), diasAuditoria);
    }
}
