package com.prisaly.m3.infrastructure.config;

import com.prisaly.m3.application.port.AutorizacionConsultaCasoPort;
import com.prisaly.m3.application.port.CaseAuthorizationPort;
import com.prisaly.m3.application.port.CasoCurrentUserPort;
import com.prisaly.m3.application.port.CasoPublicationPort;
import com.prisaly.m3.application.port.CasoRepositoryPort;
import com.prisaly.m3.application.port.CasoUpdatePort;
import com.prisaly.m3.application.service.CasoContenidoService;
import com.prisaly.m3.application.service.CasoDocenteConsultaService;
import com.prisaly.m3.application.service.CasoPersistentUpdateService;
import com.prisaly.m3.application.service.CasoTransicionService;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Ensambla los casos de uso. Un proveedor ausente deja el servicio cerrado, nunca concede acceso. */
@Configuration(proxyBeanMethods = false)
public class M3AplicacionConfig {
    @Bean
    CasoDocenteConsultaService casoDocenteConsultaService(ObjectProvider<CasoCurrentUserPort> identidad,
            ObjectProvider<AutorizacionConsultaCasoPort> autorizacion, CasoRepositoryPort repositorio,
            ObjectProvider<CasoUpdatePort> lectorVersionado) {
        return new CasoDocenteConsultaService(identidad.getIfAvailable(), autorizacion.getIfAvailable(), repositorio,
                lectorVersionado.getIfAvailable());
    }

    @Bean
    CasoPersistentUpdateService casoPersistentUpdateService(ObjectProvider<CasoCurrentUserPort> identidad,
            ObjectProvider<CaseAuthorizationPort> autorizacion, CasoRepositoryPort repositorio) {
        return new CasoPersistentUpdateService(identidad.getIfAvailable(), autorizacion.getIfAvailable(), repositorio);
    }

    @Bean
    CasoContenidoService casoContenidoService(ObjectProvider<CasoCurrentUserPort> identidad,
            ObjectProvider<CaseAuthorizationPort> autorizacion, ObjectProvider<CasoUpdatePort> actualizaciones) {
        return new CasoContenidoService(identidad.getIfAvailable(), autorizacion.getIfAvailable(),
                actualizaciones.getIfAvailable());
    }

    @Bean
    CasoTransicionService casoTransicionService(ObjectProvider<CasoCurrentUserPort> identidad,
            ObjectProvider<CaseAuthorizationPort> autorizacion, ObjectProvider<CasoPublicationPort> publicacion) {
        return new CasoTransicionService(identidad.getIfAvailable(), autorizacion.getIfAvailable(),
                publicacion.getIfAvailable());
    }
}
