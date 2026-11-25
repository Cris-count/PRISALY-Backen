package com.prisaly.config.m6;

import com.prisaly.m1.application.port.IdentityAccessPort;
import com.prisaly.m5.application.acceso.ControlAccesoIntentos;
import com.prisaly.m5.application.acceso.IdentidadEstudianteActual;
import com.prisaly.m5.application.acceso.TitularidadIntentos;
import com.prisaly.m6.application.port.ConsultaCalificacionPort;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * N2 CANDIDATE (DC-BE-CIERRE-DEFINITIVO-87). With {@code prisaly.m6.liberacion.enabled} absent or {@code false} (shipped)
 * nothing exists: no bean, both routes 404, and the teacher views keep {@code liberada=false}. Turning it on fails
 * startup, instead of degrading, when the M6 evaluation is off ({@code M6-LIBERACION-REQUIERE-EVALUACION}) or the
 * candidate table V019 is absent ({@code M6-LIBERACION-REQUIERE-V019}).
 */
@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(name = M6LiberacionConfig.PROPIEDAD, havingValue = "true")
public class M6LiberacionConfig {
    public static final String PROPIEDAD = "prisaly.m6.liberacion.enabled";
    /** RF-27 candidate (DC-88). Only meaningful with N2 on; with N2 off this configuration does not exist. */
    public static final String PROPIEDAD_NOTIFICACION = "prisaly.m7.notificacion-calificacion.enabled";

    @Bean
    LiberacionCalificacionM6 liberacionCalificacionM6(ObjectProvider<EvaluacionDocenteM6> evaluacion,
            ConsultaCalificacionPort calificaciones, IdentityAccessPort identidad, IdentidadEstudianteActual estudiante,
            TitularidadIntentos titularidad, JdbcTemplate jdbc,
            org.springframework.core.env.Environment entorno,
            ObjectProvider<org.springframework.transaction.PlatformTransactionManager> transacciones) {
        EvaluacionDocenteM6 m6 = evaluacion.getIfAvailable();
        if (m6 == null) {
            throw new IllegalStateException("M6-LIBERACION-REQUIERE-EVALUACION: " + PROPIEDAD
                    + "=true necesita prisaly.m6.evaluacion.enabled=true");
        }
        Boolean tabla = jdbc.queryForObject("SELECT to_regclass('public.\"LiberacionCalificacion\"') IS NOT NULL", Boolean.class);
        if (!Boolean.TRUE.equals(tabla)) {
            throw new IllegalStateException("M6-LIBERACION-REQUIERE-V019: falta la tabla candidata LiberacionCalificacion"
                    + " (db/m6-propuesta, sujeta a D-ESQ-1)");
        }
        var acceso = new ControlAccesoIntentos(estudiante, titularidad);
        // RF-27 minimal candidate (DC-88): notice + email of the first release, in the release transaction.
        if (entorno.getProperty(PROPIEDAD_NOTIFICACION, Boolean.class, false)) {
            return new LiberacionCalificacionM6(m6, calificaciones, identidad, acceso, jdbc,
                    new NotificacionCalificacionM7(jdbc),
                    new org.springframework.transaction.support.TransactionTemplate(transacciones.getObject()));
        }
        return new LiberacionCalificacionM6(m6, calificaciones, identidad, acceso, jdbc);
    }
}
