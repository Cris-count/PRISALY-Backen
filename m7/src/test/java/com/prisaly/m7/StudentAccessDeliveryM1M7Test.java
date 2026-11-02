package com.prisaly.m7;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.prisaly.m1.application.port.StudentAccessDeliveryPort;
import com.prisaly.m1.application.service.StudentAccessCodeIssuanceService;
import com.prisaly.m1.application.service.StudentAccessCodeIssuanceService.IssuedAccessCode;
import com.prisaly.m1.application.service.StudentAccessValidationService;
import com.prisaly.m1.presentation.EstudianteAccessController;
import com.prisaly.m1.presentation.IdentityExceptionHandler;
import com.prisaly.m4.application.port.NotificacionPort;
import com.prisaly.m7.infrastructure.InMemoryStudentAccessDeliveryAdapter;
import com.prisaly.m7.infrastructure.InMemoryStudentAccessDeliveryAdapter.EstadoEntrega;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

/**
 * Evidence: M1 issues a challenge, M7 enqueues via {@link StudentAccessDeliveryPort}
 * (not {@link NotificacionPort}), prep delivery without SMTP, then {@code /validar} consumes it.
 */
class StudentAccessDeliveryM1M7Test {

    private static final UUID USER_ID = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa");
    private static final UUID STUDENT_ID = UUID.fromString("bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb");

    private StudentAccessValidationService validationService;
    private InMemoryStudentAccessDeliveryAdapter outbox;
    private StudentAccessCodeIssuanceService issuance;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        validationService = new StudentAccessValidationService();
        outbox = new InMemoryStudentAccessDeliveryAdapter();
        StudentAccessDeliveryPort deliveryPort = outbox;
        issuance = new StudentAccessCodeIssuanceService(validationService, deliveryPort);
        mockMvc = MockMvcBuilders
                .standaloneSetup(new EstudianteAccessController(validationService, true))
                .setControllerAdvice(new IdentityExceptionHandler())
                .build();
    }

    @Test
    void m7EnqueuesAndPrepDelivery_allowsValidarToConsumeCode() throws Exception {
        assertInstanceOf(StudentAccessDeliveryPort.class, outbox);
        assertNotEquals(NotificacionPort.class, StudentAccessDeliveryPort.class);

        IssuedAccessCode issued = issuance.emitirCodigoDePrueba(
                "challenge-m1-m7-40pct",
                "87654321",
                "estudiante.prueba@prisaly.test",
                USER_ID,
                STUDENT_ID,
                Instant.now().plus(5, ChronoUnit.MINUTES),
                "idem-m1-m7-40pct");

        assertEquals(1, outbox.registros().size());
        assertEquals(EstadoEntrega.PENDIENTE, outbox.registros().getFirst().estado());
        assertEquals(1, outbox.entregarPendientesSinSmtp());
        assertEquals(EstadoEntrega.ENTREGADO_PREP, outbox.registros().getFirst().estado());

        mockMvc.perform(post("/v1/acceso-estudiante/validar")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"challengeId":"%s","codigo":"%s"}
                                """.formatted(issued.challengeId(), issued.codigo())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.userId").value(USER_ID.toString()))
                .andExpect(jsonPath("$.studentId").value(STUDENT_ID.toString()))
                .andExpect(jsonPath("$.challengeId").value(issued.challengeId()))
                .andExpect(jsonPath("$.estado").value(StudentAccessValidationService.ESTADO_VALIDADO));
    }
}
