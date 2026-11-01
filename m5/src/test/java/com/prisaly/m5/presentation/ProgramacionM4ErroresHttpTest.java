package com.prisaly.m5.presentation;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.prisaly.m4.api.ConsultaProgramacionNoDisponibleException;

import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/** Prueba del borde HTTP de M5, sin publicar un endpoint de estudiante. */
class ProgramacionM4ErroresHttpTest {
    @Test
    void indisponibilidadM4Devuelve503SinFiltrarLaCausaSql() throws Exception {
        var mvc =
                MockMvcBuilders.standaloneSetup(new ConsultaDePrueba())
                        .setControllerAdvice(new ManejadorErroresM5())
                        .build();

        mvc.perform(get("/prueba-m5/programacion"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(
                        jsonPath("$.code").value(ConsultaProgramacionNoDisponibleException.CODIGO))
                .andExpect(jsonPath("$.message").isNotEmpty())
                .andExpect(content().string(not(containsString("SELECT secreto"))));
    }

    @RestController
    static class ConsultaDePrueba {
        @GetMapping("/prueba-m5/programacion")
        public void consultar() {
            throw new ConsultaProgramacionNoDisponibleException(
                    new IllegalStateException("SELECT secreto FROM tabla_interna"));
        }
    }
}
