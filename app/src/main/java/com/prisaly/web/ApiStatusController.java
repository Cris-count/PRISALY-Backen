package com.prisaly.web;

import java.util.Map;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/** Comprueba que el proceso HTTP está vivo. No consulta la base ni la sesión. */
@RestController
public class ApiStatusController {

    /** Responde 200 con el nombre del servicio. Postman lo usa antes de cualquier flujo. */
    @GetMapping("/health")
    public Map<String, String> health() {
        return Map.of("status", "ok", "service", "prisaly-backend");
    }
}
