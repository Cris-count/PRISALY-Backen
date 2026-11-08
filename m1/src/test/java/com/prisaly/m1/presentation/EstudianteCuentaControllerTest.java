package com.prisaly.m1.presentation;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.prisaly.m1.application.service.ActivacionCuentaEstudianteService;
import com.prisaly.m1.application.service.AutenticacionEstudianteService;
import com.prisaly.m1.application.support.CuentaEstudianteMemoria;
import com.prisaly.m1.domain.student.ClaveHmacEstudiante;
import com.prisaly.m1.infrastructure.security.EstudianteSessionFilter;
import com.prisaly.m1.infrastructure.security.PasswordHashConfig;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class EstudianteCuentaControllerTest {

    @Test
    void conElFlagApagadoLasRutasSiguenEn501() throws Exception {
        MockMvc mockMvc = mockMvc(false, null, null);
        mockMvc.perform(post("/v1/acceso-estudiante/activar")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"challengeId":"11111111-1111-1111-1111-111111111111","codigo":"12345678","contrasena":"contrasena-segura"}
                                """))
                .andExpect(status().isNotImplemented());
        mockMvc.perform(post("/v1/acceso-estudiante/autenticar")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"correo":"ana.estudiante@prisaly.test","contrasena":"contrasena-segura"}
                                """))
                .andExpect(status().isNotImplemented())
                .andExpect(header().doesNotExist("Set-Cookie"));
    }

    @Test
    void autenticarEntregaCookieYNoElIdentificador() throws Exception {
        UUID usuario = UUID.randomUUID();
        CuentaEstudianteMemoria cuentas = new CuentaEstudianteMemoria();
        cuentas.vincular(usuario, UUID.randomUUID(), "ana.estudiante@prisaly.test", false, null);
        var encoder = new PasswordHashConfig().passwordEncoder();
        var clave = new ClaveHmacEstudiante("clave-hmac-prueba".getBytes(StandardCharsets.UTF_8));
        var activacion = new ActivacionCuentaEstudianteService(cuentas, encoder, clave);
        var emitido = activacion.emitir(usuario);
        MockMvc mockMvc = mockMvc(true, activacion, new AutenticacionEstudianteService(cuentas, encoder, clave));
        mockMvc.perform(post("/v1/acceso-estudiante/activar")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"challengeId":"%s","codigo":"%s","contrasena":"contrasena-segura"}
                                """.formatted(emitido.challengeId(), emitido.codigo())))
                .andExpect(status().isNoContent());
        mockMvc.perform(post("/v1/acceso-estudiante/autenticar")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"correo":"ana.estudiante@prisaly.test","contrasena":"contrasena-segura"}
                                """))
                .andExpect(status().isNoContent())
                .andExpect(header().string("Set-Cookie", org.hamcrest.Matchers.containsString(EstudianteSessionFilter.COOKIE)))
                .andExpect(header().string("Set-Cookie", org.hamcrest.Matchers.containsString("HttpOnly")));
    }

    private static MockMvc mockMvc(
            boolean enabled,
            ActivacionCuentaEstudianteService activacion,
            AutenticacionEstudianteService autenticacion) {
        DefaultListableBeanFactory factory = new DefaultListableBeanFactory();
        if (activacion != null) {
            factory.registerSingleton("activacion", activacion);
        }
        if (autenticacion != null) {
            factory.registerSingleton("autenticacion", autenticacion);
        }
        return MockMvcBuilders.standaloneSetup(new EstudianteCuentaController(
                        enabled,
                        factory.getBeanProvider(ActivacionCuentaEstudianteService.class),
                        factory.getBeanProvider(AutenticacionEstudianteService.class)))
                .setControllerAdvice(new IdentityExceptionHandler())
                .build();
    }
}
