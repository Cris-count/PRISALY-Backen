package com.prisaly.m5;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.prisaly.m5.application.acceso.InvestigacionProtegida;
import com.prisaly.m5.application.servicio.InvestigacionServicio;
import com.prisaly.m5.infrastructure.ConfiguracionParticipacion;
import com.prisaly.m5.presentation.rest.InvestigacionM5Controlador;
import org.junit.jupiter.api.Test;

/** DC-111-CIERRE: el controlador y la configuración usan el mismo interruptor y el mismo contrato protegido. */
class InvestigacionPropiedadTest {

    @Test
    void controladorYConfiguracionCompartenElInterruptor() {
        assertEquals(ConfiguracionParticipacion.PROPIEDAD_INVESTIGACION_HTTP, InvestigacionM5Controlador.PROPIEDAD);
    }

    @Test
    void elServicioCumpleElContratoProtegidoQueRecibePresentacion() {
        assertTrue(InvestigacionProtegida.class.isAssignableFrom(InvestigacionServicio.class));
    }
}
