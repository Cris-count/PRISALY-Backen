package com.prisaly.m1.application.service;

import com.prisaly.m1.application.port.AccesoEstudiantePort;
import com.prisaly.m1.domain.student.ClavesAccesoEstudiante;
import com.prisaly.m1.domain.student.PoliticaCodigoAcceso;
import java.time.Clock;
import java.time.OffsetDateTime;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** D-B2.6: immediate, idempotent revocation of the current student session (any active K-SES version). */
@Service
@ConditionalOnProperty(name = "prisaly.m1.student-access.enabled", havingValue = "true")
public class CierreSesionEstudianteService {

    private final AccesoEstudiantePort accesos;
    private final ClavesAccesoEstudiante claves;
    private final Clock reloj;

    @Autowired
    public CierreSesionEstudianteService(AccesoEstudiantePort accesos, ClavesAccesoEstudiante claves) {
        this(accesos, claves, Clock.systemUTC());
    }

    public CierreSesionEstudianteService(AccesoEstudiantePort accesos, ClavesAccesoEstudiante claves, Clock reloj) {
        this.accesos = accesos;
        this.claves = claves;
        this.reloj = reloj;
    }

    /**
     * @param origen TCP peer address, recorded in the CIERRE audit row only when a session was actually revoked
     */
    @Transactional
    public void cerrar(String token, String origen) {
        if (token == null || token.isBlank()) {
            return;
        }
        OffsetDateTime ahora = OffsetDateTime.now(reloj);
        accesos.revocarSesion(claves.sesion().firmasCandidatas(PoliticaCodigoAcceso.mensajeSesion(token)), ahora)
                .ifPresent(estudiante -> accesos.auditar("CIERRE", estudiante, "SESION_REVOCADA", origen, ahora));
    }
}
