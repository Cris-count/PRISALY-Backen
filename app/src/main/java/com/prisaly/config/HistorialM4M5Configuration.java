package com.prisaly.config;

import com.prisaly.m4.application.port.ConvocatoriaRepository;
import com.prisaly.m4.application.port.ParticipacionesPort;
import com.prisaly.m4.domain.model.Convocatoria;
import com.prisaly.m4.domain.rule.ReglaNegocioException;
import com.prisaly.m5.api.HistorialParticipacionPort;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/** Ensamblado entre contratos de M4 y M5, sin dependencias circulares entre módulos. */
@Configuration(proxyBeanMethods = false)
public class HistorialM4M5Configuration {
    @Bean
    ParticipacionesPort historialM4M5(
            ConvocatoriaRepository convocatorias,
            ObjectProvider<HistorialParticipacionPort> proveedores) {
        return sesionId -> {
            // El servicio M4 ya mantiene el bloqueo de la sesión hasta el commit.
            if (!TransactionSynchronizationManager.isActualTransactionActive()
                    || TransactionSynchronizationManager.isCurrentTransactionReadOnly()) {
                throw new IllegalStateException(
                        "Historial M4–M5 requiere una transacción de escritura");
            }
            try {
                var historial = proveedores.getIfAvailable();
                if (historial == null) {
                    throw new IllegalStateException("Proveedor de historial M5 ausente");
                }
                var ids =
                        convocatorias.listarPorSesion(sesionId).stream()
                                .map(Convocatoria::getId)
                                .toList();
                // No se filtran retiradas: el historial académico permanece.
                return historial.existeParticipacion(ids);
            } catch (RuntimeException causa) {
                var error =
                        new ReglaNegocioException(
                                "M4-PROVEEDOR-NO-DISPONIBLE",
                                "No se pudo verificar el historial académico de M5");
                error.initCause(causa);
                throw error;
            }
        };
    }
}
