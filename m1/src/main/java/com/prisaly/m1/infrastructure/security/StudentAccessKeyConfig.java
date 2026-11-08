package com.prisaly.m1.infrastructure.security;

import com.prisaly.m1.domain.student.ClaveHmacEstudiante;
import java.nio.charset.StandardCharsets;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class StudentAccessKeyConfig {

    @Bean
    @ConditionalOnProperty(name = "prisaly.m1.student-access.enabled", havingValue = "true")
    ClaveHmacEstudiante claveHmacEstudiante(
            @Value("${prisaly.m1.student-access.hmac-secret}") String secreto) {
        return new ClaveHmacEstudiante(secreto.getBytes(StandardCharsets.UTF_8));
    }
}
