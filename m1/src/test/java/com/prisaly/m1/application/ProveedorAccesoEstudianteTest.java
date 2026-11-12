package com.prisaly.m1.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

import com.prisaly.m1.application.port.AccesoEstudiantePort;
import com.prisaly.m1.application.port.StudentAccessPort;
import com.prisaly.m1.application.service.DenyingStudentAccessService;
import com.prisaly.m1.application.service.PurgaAccesoEstudianteService;
import com.prisaly.m1.application.service.SessionStudentAccessService;
import com.prisaly.m1.application.support.AccesoEstudianteMemoria;
import com.prisaly.m1.application.support.ClavesDePrueba;
import com.prisaly.m1.domain.student.ClavesAccesoEstudiante;
import com.prisaly.m1.infrastructure.security.RequestTokenSesionEstudianteActual;
import com.prisaly.m1.infrastructure.security.StudentAccessKeyConfig;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

/** Flag OFF keeps the denial; flag ON requires valid, independent K-COD and K-SES or the context fails (D-B5). */
class ProveedorAccesoEstudianteTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withUserConfiguration(DenyingStudentAccessService.class, SessionStudentAccessService.class,
                    RequestTokenSesionEstudianteActual.class, StudentAccessKeyConfig.class,
                    PurgaAccesoEstudianteService.class)
            .withBean(AccesoEstudiantePort.class, AccesoEstudianteMemoria::new);

    @Test
    void elFlagApagadoDejaSoloLaDenegacionYNoExigeClaves() {
        runner.withPropertyValues("prisaly.m1.student-access.enabled=false").run(context -> {
            assertEquals(1, context.getBeansOfType(StudentAccessPort.class).size());
            assertInstanceOf(DenyingStudentAccessService.class, context.getBean(StudentAccessPort.class));
            assertThat(context).doesNotHaveBean(ClavesAccesoEstudiante.class);
        });
    }

    @Test
    void elFlagEncendidoConClavesValidasSustituyeLaDenegacionPorLaSesion() {
        runner.withPropertyValues("prisaly.m1.student-access.enabled=true",
                "prisaly.m1.student-access.claves.codigo=" + ClavesDePrueba.especificacion("cod-1", 32),
                "prisaly.m1.student-access.claves.sesion=" + ClavesDePrueba.especificacion("ses-1", 32))
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertInstanceOf(SessionStudentAccessService.class, context.getBean(StudentAccessPort.class));
                });
    }

    @Test
    void elFlagEncendidoNoArrancaConClavesAusentesCortasOCompartidas() {
        String compartida = ClavesDePrueba.especificacion("k", 32);
        for (String[] props : new String[][] {
                {"prisaly.m1.student-access.enabled=true"},
                {"prisaly.m1.student-access.enabled=true", "prisaly.m1.student-access.claves.codigo=" + ClavesDePrueba.especificacion("c", 16),
                        "prisaly.m1.student-access.claves.sesion=" + ClavesDePrueba.especificacion("s", 32)},
                {"prisaly.m1.student-access.enabled=true", "prisaly.m1.student-access.claves.codigo=" + compartida,
                        "prisaly.m1.student-access.claves.sesion=" + compartida},
                // The former single text secret is no longer accepted.
                {"prisaly.m1.student-access.enabled=true", "prisaly.m1.student-access.hmac-secret=0123456789abcdef"}}) {
            runner.withPropertyValues(props).run(context -> assertThat(context).hasFailed());
        }
    }
}
