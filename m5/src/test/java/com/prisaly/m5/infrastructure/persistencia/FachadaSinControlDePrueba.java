package com.prisaly.m5.infrastructure.persistencia;

import org.springframework.transaction.PlatformTransactionManager;

import com.prisaly.m5.application.ParticipacionFacade;
import com.prisaly.m5.application.port.RepositorioIntentos;
import com.prisaly.m5.application.servicio.ActualizarRespuestaServicio;
import com.prisaly.m5.application.servicio.ConsultarIntentoServicio;
import com.prisaly.m5.application.servicio.FinalizarIntentoServicio;
import com.prisaly.m5.application.servicio.RegistrarRespuestaServicio;
import com.prisaly.m5.infrastructure.FachadaParticipacionTransaccional;

/**
 * Solo pruebas de persistencia: la fachada transaccional SIN control de acceso,
 * para probar casos de uso, transacciones y concurrencia contra PostgreSQL.
 * En producción no existe como bean (ver {@code ConfiguracionParticipacion}).
 */
final class FachadaSinControlDePrueba {

    private FachadaSinControlDePrueba() {
    }

    static FachadaParticipacionTransaccional ensamblar(
            RepositorioIntentos repositorio, PlatformTransactionManager transacciones) {
        return new FachadaParticipacionTransaccional(
                new ParticipacionFacade(
                        new ConsultarIntentoServicio(repositorio),
                        new RegistrarRespuestaServicio(repositorio),
                        new ActualizarRespuestaServicio(repositorio),
                        new FinalizarIntentoServicio(repositorio)),
                transacciones);
    }
}
