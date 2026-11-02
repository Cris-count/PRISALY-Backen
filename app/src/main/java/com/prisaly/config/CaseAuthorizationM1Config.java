package com.prisaly.config;

import com.prisaly.m3.application.port.CaseAuthorizationPort;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Fail-closed {@link CaseAuthorizationPort} until M1/M3 ratify permission codes and
 * case ownership (idDocente vs caller). Does not treat CREAR_CASO as publish/modify
 * authority over arbitrary cases. Never returns true while that mapping is open.
 */
@Configuration(proxyBeanMethods = false)
public class CaseAuthorizationM1Config {

    @Bean
    CaseAuthorizationPort caseAuthorizationFailClosed() {
        return new CaseAuthorizationPort() {
            @Override
            public boolean canModifyCase(String userId, String caseId) {
                return false;
            }

            @Override
            public boolean canPublishCase(String userId, String caseId) {
                return false;
            }
        };
    }
}
