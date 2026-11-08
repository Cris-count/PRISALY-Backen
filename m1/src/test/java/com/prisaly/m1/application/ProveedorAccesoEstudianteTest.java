package com.prisaly.m1.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

import com.prisaly.m1.application.port.CuentaEstudiantePort;
import com.prisaly.m1.application.port.StudentAccessPort;
import com.prisaly.m1.application.service.DenyingStudentAccessService;
import com.prisaly.m1.application.service.SessionStudentAccessService;
import com.prisaly.m1.application.support.CuentaEstudianteMemoria;
import com.prisaly.m1.infrastructure.security.RequestTokenSesionEstudianteActual;
import com.prisaly.m1.infrastructure.security.StudentAccessKeyConfig;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

class ProveedorAccesoEstudianteTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withUserConfiguration(
                    DenyingStudentAccessService.class,
                    SessionStudentAccessService.class,
                    RequestTokenSesionEstudianteActual.class,
                    StudentAccessKeyConfig.class)
            .withBean(CuentaEstudiantePort.class, CuentaEstudianteMemoria::new);

    @Test
    void elFlagApagadoDejaSoloLaDenegacion() {
        runner.withPropertyValues("prisaly.m1.student-access.enabled=false")
                .run(context -> {
                    assertEquals(1, context.getBeansOfType(StudentAccessPort.class).size());
                    assertInstanceOf(DenyingStudentAccessService.class, context.getBean(StudentAccessPort.class));
                });
    }

    @Test
    void elFlagEncendidoSustituyeLaDenegacionPorLaSesion() {
        runner.withPropertyValues(
                        "prisaly.m1.student-access.enabled=true",
                        "prisaly.m1.student-access.hmac-secret=0123456789abcdef")
                .run(context -> {
                    assertEquals(1, context.getBeansOfType(StudentAccessPort.class).size());
                    assertInstanceOf(SessionStudentAccessService.class, context.getBean(StudentAccessPort.class));
                });
    }
}
