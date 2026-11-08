package com.prisaly.m1.presentation;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.prisaly.m1.application.service.StudentAccessValidationService;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

/**
 * HTTP proof for {@code POST /v1/acceso-estudiante/validar}.
 * Productive default remains disabled (501); enabled path exercises validation only in harness.
 */
class EstudianteAccessValidarTest {

    private static final String CHALLENGE_ID = "challenge-dd02-40pct";
    private static final String CODIGO = "12345678";
    private static final UUID USER_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID STUDENT_ID = UUID.fromString("22222222-2222-2222-2222-222222222222");

    private StudentAccessValidationService validationService;

    @BeforeEach
    void setUp() {
        validationService = new StudentAccessValidationService();
    }

    @Test
    void validar_whenDisabled_returns501() throws Exception {
        MockMvc mockMvc = mockMvc(false);

        mockMvc.perform(post("/v1/acceso-estudiante/validar")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"challengeId":"challenge-dd02-40pct","codigo":"12345678"}
                                """))
                .andExpect(status().isNotImplemented())
                .andExpect(jsonPath("$.code")
                        .value(EstudianteAccessController.CODE_ESTUDIANTE_ACCESS_PENDING));
    }

    @Test
    void validar_whenEnabled_withRegisteredChallenge_returnsValidatedStudentIdentity() throws Exception {
        validationService.registerChallenge(CHALLENGE_ID, CODIGO, USER_ID, STUDENT_ID);
        MockMvc mockMvc = mockMvc(true);

        mockMvc.perform(post("/v1/acceso-estudiante/validar")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"challengeId":"challenge-dd02-40pct","codigo":"12345678"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.userId").value(USER_ID.toString()))
                .andExpect(jsonPath("$.studentId").value(STUDENT_ID.toString()))
                .andExpect(jsonPath("$.challengeId").value(CHALLENGE_ID))
                .andExpect(jsonPath("$.estado").value(StudentAccessValidationService.ESTADO_VALIDADO))
                .andExpect(header().doesNotExist("Set-Cookie"));
    }

    @Test
    void validar_whenEnabled_withUnknownChallenge_deniesAccess() throws Exception {
        MockMvc mockMvc = mockMvc(true);

        mockMvc.perform(post("/v1/acceso-estudiante/validar")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"challengeId":"missing","codigo":"12345678"}
                                """))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code")
                        .value(StudentAccessValidationService.CODE_STUDENT_ACCESS_DENIED));
    }

    @Test
    void validar_whenEnabled_consumesChallenge_secondCallIsDenied() throws Exception {
        validationService.registerChallenge(CHALLENGE_ID, CODIGO, USER_ID, STUDENT_ID);
        MockMvc mockMvc = mockMvc(true);

        mockMvc.perform(post("/v1/acceso-estudiante/validar")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"challengeId":"challenge-dd02-40pct","codigo":"12345678"}
                                """))
                .andExpect(status().isOk());

        mockMvc.perform(post("/v1/acceso-estudiante/validar")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"challengeId":"challenge-dd02-40pct","codigo":"12345678"}
                                """))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code")
                        .value(StudentAccessValidationService.CODE_STUDENT_ACCESS_DENIED));
    }

    private MockMvc mockMvc(boolean studentAccessEnabled) {
        return MockMvcBuilders
                .standaloneSetup(new EstudianteAccessController(validationService, studentAccessEnabled))
                .setControllerAdvice(new IdentityExceptionHandler())
                .build();
    }
}
