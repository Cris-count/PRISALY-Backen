package com.prisaly.config;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.prisaly.m4.infrastructure.config.M4SchemaContract;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

/**
 * DC-BE-SIM-B1-B2A-24: guardas de esquema de las fases candidatas y de DD-02 B con los contratos narrativos. Con los
 * interruptores en su valor de producción no se registra ni se comprueba nada.
 */
class EsquemaFasesIntentoConfigTest {
    private final ApplicationContextRunner runner =
            new ApplicationContextRunner().withUserConfiguration(EsquemaFasesIntentoConfig.class);

    @Test
    void sinElInterruptorNoSeRegistraNada() {
        runner.run(ctx -> {
            assertNull(ctx.getStartupFailure());
            assertTrue(ctx.getBeansOfType(EsquemaFasesIntentoConfig.class).isEmpty());
        });
    }

    @Test
    void conElInterruptorYUnContratoSinV018ElArranqueFallaCerrado() {
        for (String contrato : new String[] {M4SchemaContract.VIGENTE, M4SchemaContract.V013_SIM_A,
                M4SchemaContract.V013_DD02B, M4SchemaContract.V013_DD02B_SIM_A}) {
            runner.withPropertyValues("prisaly.m5.exploracion.fases.enabled=true", "prisaly.m4.schema-contract=" + contrato)
                    .run(ctx -> {
                        assertNotNull(ctx.getStartupFailure(), contrato);
                        assertTrue(String.valueOf(ctx.getStartupFailure().getMessage()).contains(EsquemaFasesIntentoConfig.CODIGO));
                    });
            var error = assertThrows(IllegalStateException.class, () -> EsquemaFasesIntentoConfig.verificar(contrato));
            assertTrue(error.getMessage().startsWith(EsquemaFasesIntentoConfig.CODIGO), error.getMessage());
        }
    }

    @Test
    void conElInterruptorYUnContratoSimAbArranca() {
        for (String contrato : new String[] {M4SchemaContract.V013_SIM_AB, M4SchemaContract.V013_DD02B_SIM_AB}) {
            runner.withPropertyValues("prisaly.m5.exploracion.fases.enabled=true", "prisaly.m4.schema-contract=" + contrato)
                    .run(ctx -> assertNull(ctx.getStartupFailure()));
        }
    }

    @Test
    void elAccesoEstudiantilAceptaSoloContratosConDd02b() {
        for (String contrato : new String[] {M4SchemaContract.V013_DD02B, M4SchemaContract.V013_DD02B_SIM_A,
                M4SchemaContract.V013_DD02B_SIM_AB}) {
            assertDoesNotThrow(() -> EsquemaAccesoEstudianteConfig.verificar(contrato));
        }
        for (String contrato : new String[] {M4SchemaContract.V013_SIM_A, M4SchemaContract.V013_SIM_AB, M4SchemaContract.VIGENTE}) {
            var error = assertThrows(IllegalStateException.class, () -> EsquemaAccesoEstudianteConfig.verificar(contrato));
            assertTrue(error.getMessage().startsWith(EsquemaAccesoEstudianteConfig.CODIGO), error.getMessage());
        }
    }
}
