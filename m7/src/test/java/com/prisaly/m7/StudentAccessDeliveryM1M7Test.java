package com.prisaly.m7;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

import com.prisaly.m1.application.port.CodigoAccesoDeliveryCommand;
import com.prisaly.m1.application.port.StudentAccessDeliveryPort;
import com.prisaly.m4.application.port.NotificacionPort;
import com.prisaly.m7.infrastructure.InMemoryStudentAccessDeliveryAdapter;
import com.prisaly.m7.infrastructure.InMemoryStudentAccessDeliveryAdapter.EstadoEntrega;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import org.junit.jupiter.api.Test;

/**
 * Evidence: M7 implements M1's {@link StudentAccessDeliveryPort} (not {@link NotificacionPort})
 * and prepares delivery without SMTP. The M1 in-memory issuance/validation that used to feed this
 * test was removed in DC-M1-POSTCIERRE-02; issuance under alternative A is still pending.
 */
class StudentAccessDeliveryM1M7Test {

    @Test
    void m7EncolaYPreparaLaEntregaDeUnCodigoDeM1() {
        InMemoryStudentAccessDeliveryAdapter outbox = new InMemoryStudentAccessDeliveryAdapter();
        StudentAccessDeliveryPort deliveryPort = outbox;
        assertInstanceOf(StudentAccessDeliveryPort.class, outbox);
        assertNotEquals(NotificacionPort.class, StudentAccessDeliveryPort.class);

        deliveryPort.entregarCodigo(new CodigoAccesoDeliveryCommand(
                "challenge-m1-m7",
                "estudiante.prueba@prisaly.test",
                "87654321",
                Instant.now().plus(5, ChronoUnit.MINUTES),
                "idem-m1-m7"));

        assertEquals(1, outbox.registros().size());
        assertEquals(EstadoEntrega.PENDIENTE, outbox.registros().getFirst().estado());
        assertEquals(1, outbox.entregarPendientesSinSmtp());
        assertEquals(EstadoEntrega.ENTREGADO_PREP, outbox.registros().getFirst().estado());
    }
}
