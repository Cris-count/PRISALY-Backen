package com.prisaly.m4;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Aplicación de prueba para cargar el contexto de Spring Boot en los tests de integración de M4.
 */
@SpringBootApplication
public class TestM4Application {
    public static void main(String[] args) {
        SpringApplication.run(TestM4Application.class, args);
    }
}
