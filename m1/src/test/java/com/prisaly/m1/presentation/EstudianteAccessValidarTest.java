package com.prisaly.m1.presentation;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.lang.reflect.Constructor;
import java.util.Arrays;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

/**
 * {@code POST /v1/acceso-estudiante/validar} after DC-M1-POSTCIERRE-02: no in-memory challenge
 * remains, so the route cannot validate a code nor disclose an identity in any configuration.
 */
class EstudianteAccessValidarTest {

    private final MockMvc mockMvc = MockMvcBuilders
            .standaloneSetup(new EstudianteAccessController())
            .setControllerAdvice(new IdentityExceptionHandler())
            .build();

    @Test
    void validar_conCuerpoValido_responde501SinIdentidadNiCookie() throws Exception {
        mockMvc.perform(post("/v1/acceso-estudiante/validar")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"challengeId":"challenge-dd02","codigo":"12345678"}
                                """))
                .andExpect(status().isNotImplemented())
                .andExpect(jsonPath("$.code").value(EstudianteAccessController.CODE_ESTUDIANTE_ACCESS_PENDING))
                .andExpect(jsonPath("$.userId").doesNotExist())
                .andExpect(jsonPath("$.studentId").doesNotExist())
                .andExpect(header().doesNotExist("Set-Cookie"));
    }

    @Test
    void validar_conCuerpoInvalido_responde400() throws Exception {
        mockMvc.perform(post("/v1/acceso-estudiante/validar")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"challengeId":"challenge-dd02","codigo":"123"}
                                """))
                .andExpect(status().isBadRequest());
    }

    // The controller cannot be wired to any code store or to the student-access flag.
    @Test
    void elControladorNoDependeDeNingunAlmacenDeRetosNiDelFlag() {
        Constructor<?>[] constructors = EstudianteAccessController.class.getConstructors();
        assertFalse(Arrays.stream(constructors).anyMatch(constructor -> constructor.getParameterCount() > 0));
    }
}
