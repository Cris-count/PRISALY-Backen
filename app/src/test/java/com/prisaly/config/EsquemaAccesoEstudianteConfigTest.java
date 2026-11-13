package com.prisaly.config;

import static org.junit.jupiter.api.Assertions.*;

import com.prisaly.m4.infrastructure.config.M4SchemaContract;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

/** DD-02 B (CIERRE-07, P2): student access can only start on the DD-02 B schema contract; otherwise it fails closed. */
class EsquemaAccesoEstudianteConfigTest {

    private final ApplicationContextRunner runner =
            new ApplicationContextRunner().withUserConfiguration(EsquemaAccesoEstudianteConfig.class);

    @Test
    void conElFlagDesactivadoNoHayVerificacionNiCambioDeComportamiento() {
        runner.run(ctx -> {
            assertNull(ctx.getStartupFailure());
            assertFalse(ctx.containsBean("verificarEsquemaAccesoEstudiante"));
        });
        runner.withPropertyValues("prisaly.m1.student-access.enabled=false").run(ctx -> {
            assertNull(ctx.getStartupFailure());
            assertFalse(ctx.containsBean("verificarEsquemaAccesoEstudiante"));
        });
        runner.withPropertyValues("prisaly.m1.student-access.enabled=true",
                        "prisaly.m4.schema-contract=" + M4SchemaContract.V013_DD02B)
                .run(ctx -> assertTrue(ctx.containsBean("verificarEsquemaAccesoEstudiante")));
    }

    @Test
    void conElFlagYElContratoVigenteElArranqueFallaCerrado() {
        runner.withPropertyValues("prisaly.m1.student-access.enabled=true").run(ctx -> {
            assertNotNull(ctx.getStartupFailure());
            assertTrue(String.valueOf(ctx.getStartupFailure().getMessage()).contains(EsquemaAccesoEstudianteConfig.CODIGO),
                    String.valueOf(ctx.getStartupFailure()));
        });
        for (String contrato : new String[] {M4SchemaContract.VIGENTE, M4SchemaContract.HISTORICO,
                M4SchemaContract.DD02, M4SchemaContract.V013_DD02}) {
            var error = assertThrows(IllegalStateException.class, () -> EsquemaAccesoEstudianteConfig.verificar(contrato));
            assertTrue(error.getMessage().startsWith(EsquemaAccesoEstudianteConfig.CODIGO), error.getMessage());
        }
    }

    @Test
    void conElFlagYElContratoDd02bArranca() {
        runner.withPropertyValues("prisaly.m1.student-access.enabled=true",
                        "prisaly.m4.schema-contract=" + M4SchemaContract.V013_DD02B)
                .run(ctx -> assertNull(ctx.getStartupFailure()));
        assertDoesNotThrow(() -> EsquemaAccesoEstudianteConfig.verificar(M4SchemaContract.V013_DD02B));
    }

    @Test
    void elDefaultProductivoSigueDesactivado() throws Exception {
        String yml;
        try (var in = getClass().getClassLoader().getResourceAsStream("application.yml")) {
            yml = new String(in.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
        }
        assertTrue(yml.replace("\r", "").contains("student-access:\n      enabled: false"), "DD-02 must stay disabled by default");
    }
}
