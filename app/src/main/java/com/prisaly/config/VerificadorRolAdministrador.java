package com.prisaly.config;

import com.prisaly.m1.application.service.IdentityCatalogService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

/** PD-01: reports at startup whether this environment has the ADMINISTRADOR role initialized. */
@Component
public class VerificadorRolAdministrador implements ApplicationRunner {
    private static final Logger log = LoggerFactory.getLogger(VerificadorRolAdministrador.class);
    private final ObjectProvider<IdentityCatalogService> catalogo;

    public VerificadorRolAdministrador(ObjectProvider<IdentityCatalogService> catalogo) {
        this.catalogo = catalogo;
    }

    @Override
    public void run(ApplicationArguments args) {
        verificar();
    }

    /** @return true only when the catalog is available and the role exists. */
    public boolean verificar() {
        IdentityCatalogService servicio = catalogo.getIfAvailable();
        boolean presente = servicio != null && servicio.existeRolAdministrador();
        if (presente) {
            log.info("PD-01: rol ADMINISTRADOR inicializado");
        } else {
            log.warn("PD-01: rol ADMINISTRADOR ausente; no es posible asignar ni revocar permisos controlados");
        }
        return presente;
    }
}
