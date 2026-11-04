package com.prisaly.config;

import com.prisaly.m1.application.port.StudentAccessDeliveryPort;
import com.prisaly.m1.application.service.StudentAccessCodeIssuanceService;
import com.prisaly.m1.application.service.StudentAccessValidationService;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Explicit DD-02 issuance wiring where M1 validation
 * and M7 delivery coexist.
 * Presence of this bean does not enable productive
 * student authentication.
 */
@Configuration(proxyBeanMethods = false)
public class StudentAccessIssuanceConfig {

    @Bean
    StudentAccessCodeIssuanceService studentAccessCodeIssuance(
            StudentAccessValidationService validationService,
            @Qualifier("studentAccessDeliveryM7")
            StudentAccessDeliveryPort studentAccessDeliveryPort) {

        return new StudentAccessCodeIssuanceService(
                validationService,
                studentAccessDeliveryPort
        );
    }
}
