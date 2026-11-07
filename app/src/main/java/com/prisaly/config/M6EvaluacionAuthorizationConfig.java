package com.prisaly.config;

import com.prisaly.m1.application.port.IdentityAccessPort;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Explicit M1/M6 wiring. M6 does not depend on M1. No academic producer is assembled here. */
@Configuration(proxyBeanMethods = false)
public class M6EvaluacionAuthorizationConfig {
    @Bean
    public M6EvaluacionAuthorizationAdapter m6EvaluacionAuthorizationAdapter(IdentityAccessPort identity) {
        return new M6EvaluacionAuthorizationAdapter(identity);
    }
}
