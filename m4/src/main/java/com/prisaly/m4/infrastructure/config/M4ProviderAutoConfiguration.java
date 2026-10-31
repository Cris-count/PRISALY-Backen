package com.prisaly.m4.infrastructure.config;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.beans.factory.ObjectProvider;
import com.prisaly.m1.application.port.IdentityAccessPort;
import com.prisaly.m3.application.port.CasoRepositoryPort;
import com.prisaly.m4.infrastructure.adapter.IdentidadM1Adapter;
import com.prisaly.m4.infrastructure.adapter.CasoAcademicoM3Adapter;

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
    IdentidadPort identidadNoDisponible(ObjectProvider<IdentityAccessPort> proveedores) {
        var proveedor = proveedores.getIfAvailable();
        return proveedor == null ? (docenteId, permiso) -> false : new IdentidadM1Adapter(proveedor);
    }

    @Bean
    @ConditionalOnMissingBean(CasoAcademicoPort.class)
    CasoAcademicoPort casosNoDisponibles(ObjectProvider<CasoRepositoryPort> proveedores) {
        var proveedor = proveedores.getIfAvailable();
        if (proveedor != null) {
            return new CasoAcademicoM3Adapter(proveedor);
        }
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
