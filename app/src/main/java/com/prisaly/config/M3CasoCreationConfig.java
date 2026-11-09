package com.prisaly.config;

import com.prisaly.m1.application.port.IdentityAccessPort;
import com.prisaly.m3.application.port.CaseAuthorizationPort;
import com.prisaly.m3.application.port.CasoRepositoryPort;
import com.prisaly.m3.application.service.CasoApplicationService;
import com.prisaly.m3.presentation.CasoPatchConcurrencyContract;
import com.prisaly.m3.presentation.CasoStrongEtagContract;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Explicit M1/M3 wiring; no dependency from M3 to M1 and no publication bean. Registers the D8 codec. */
@Configuration(proxyBeanMethods = false)
public class M3CasoCreationConfig {
    @Bean
    public M3CaseAuthorizationAdapter m3CaseAuthorizationAdapter(IdentityAccessPort identity) {
        return new M3CaseAuthorizationAdapter(identity);
    }

    @Bean
    public CasoPatchConcurrencyContract casoStrongEtagContract() {
        return new CasoStrongEtagContract();
    }

    @Bean
    public CasoApplicationService casoApplicationService(CaseAuthorizationPort authorization,
            CasoRepositoryPort repository) {
        return new CasoApplicationService(authorization, repository);
    }
}
