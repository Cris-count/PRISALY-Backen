package com.prisaly.m1.presentation;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
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
 * End-to-end HTTP proof for {@code POST /v1/acceso-estudiante/validar}.
 * Seeds an in-memory challenge (no Flyway / OTP / SMTP / M7 in this step).
 */
class EstudianteAccessValidarTest {

    private static final String CHALLENGE_ID = "challenge-dd02-40pct";
    private static final String CODIGO = "12345678";
    private static final UUID USER_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID STUDENT_ID = UUID.fromString("22222222-2222-2222-2222-222222222222");

    private MockMvc mockMvc;
    private StudentAccessValidationService validationService;

    @BeforeEach
    void setUp() {
        validationService = new StudentAccessValidationService();
        mockMvc = MockMvcBuilders
                .standaloneSetup(new EstudianteAccessController(validationService))
                .setControllerAdvice(new IdentityExceptionHandler())
                .build();
    }

    @Test
    void validar_withRegisteredChallenge_returnsValidatedStudentIdentity() throws Exception {
        validationService.registerChallenge(CHALLENGE_ID, CODIGO, USER_ID, STUDENT_ID);

        mockMvc.perform(post("/v1/acceso-estudiante/validar")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"challengeId":"challenge-dd02-40pct","codigo":"12345678"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.userId").value(USER_ID.toString()))
                .andExpect(jsonPath("$.studentId").value(STUDENT_ID.toString()))
                .andExpect(jsonPath("$.challengeId").value(CHALLENGE_ID))
                .andExpect(jsonPath("$.estado").value(StudentAccessValidationService.ESTADO_VALIDADO));
    }

    @Test
    void validar_withUnknownChallenge_deniesAccess() throws Exception {
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
    void validar_consumesChallenge_secondCallIsDenied() throws Exception {
        validationService.registerChallenge(CHALLENGE_ID, CODIGO, USER_ID, STUDENT_ID);

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
}
