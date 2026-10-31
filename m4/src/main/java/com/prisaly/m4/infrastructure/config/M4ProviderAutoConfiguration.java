package com.prisaly.m4.infrastructure.config;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;

import com.prisaly.m4.application.port.CasoAcademicoPort;
import com.prisaly.m4.application.port.IdentidadPort;
import com.prisaly.m4.application.port.NotificacionPort;
import com.prisaly.m4.application.port.ParticipacionesPort;
import com.prisaly.m4.domain.rule.ReglaNegocioException;

/**
 * Se evalúa después de las configuraciones de proveedores. Sin implementación
 * real, ningún perfil ni propiedad convierte la ausencia en autorización.
 * Los dobles solo existen en src/test y se importan expresamente allí.
 */
@AutoConfiguration
public class M4ProviderAutoConfiguration {
    @Bean
    @ConditionalOnMissingBean(IdentidadPort.class)
    IdentidadPort identidadNoDisponible() {
        return (docenteId, permiso) -> false;
    }

    @Bean
    @ConditionalOnMissingBean(CasoAcademicoPort.class)
    CasoAcademicoPort casosNoDisponibles() {
        return new CasoAcademicoPort() {
            @Override
            public boolean estaPublicado(java.util.UUID casoId) {
                throw noDisponible("M3");
            }

            @Override
            public void solicitarBloqueoAcademico(java.util.UUID casoId) {
                throw noDisponible("M3");
            }
        };
    }

    @Bean
    @ConditionalOnMissingBean(ParticipacionesPort.class)
    ParticipacionesPort participacionesNoDisponibles() {
        return sesionId -> { throw noDisponible("M5"); };
    }

    @Bean
    @ConditionalOnMissingBean(NotificacionPort.class)
    NotificacionPort notificacionesNoDisponibles() {
        return (sesionId, estudianteId) -> { throw noDisponible("M7"); };
    }

    private static ReglaNegocioException noDisponible(String modulo) {
        return new ReglaNegocioException("M4-PROVEEDOR-NO-DISPONIBLE",
                "No está disponible el proveedor requerido de " + modulo);
    }
}
