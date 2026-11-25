package com.prisaly.m7.infrastructure;

import static org.junit.jupiter.api.Assertions.*;

import java.time.ZoneId;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

/** OBS-83-M7-02 (DC-86): presentation zone of the convocatoria dates; UTC by default, invalid zones fail fast. */
class ZonaHorariaConvocatoriaTest {

    @Test
    void sinPropiedadEsUtcYElTextoAprobadoNoCambia() {
        var env = new MockEnvironment();
        assertEquals(ZoneOffset.UTC, M7Configuration.zonaPresentacion(env));
        assertEquals("UTC", M7Configuration.etiquetaZona(M7Configuration.zonaPresentacion(env)));
        String plantilla = "Inicio: {inicioPrevisto} {zonaHoraria}. Fin: {finPrevisto} {zonaHoraria}.";
        assertEquals("Inicio: {inicioPrevisto} UTC. Fin: {finPrevisto} UTC.",
                plantilla.replace("{zonaHoraria}", M7Configuration.etiquetaZona(ZoneOffset.UTC)),
                "same approved text as before DC-86");
    }

    @Test
    void utcExplicitoTambienProduceLaEtiquetaUtc() {
        for (String valor : new String[] {"UTC", " UTC ", "Z"}) {
            var env = new MockEnvironment().withProperty("prisaly.m7.zona-horaria", valor);
            assertEquals("UTC", M7Configuration.etiquetaZona(M7Configuration.zonaPresentacion(env)), valor);
        }
    }

    @Test
    void americaBogotaMuestraZonaYDesfase() {
        var env = new MockEnvironment().withProperty("prisaly.m7.zona-horaria", "America/Bogota");
        ZoneId zona = M7Configuration.zonaPresentacion(env);
        assertEquals(ZoneId.of("America/Bogota"), zona);
        assertEquals("hora America/Bogota (UTC-05:00)", M7Configuration.etiquetaZona(zona));
    }

    @Test
    void zonaInvalidaFallaAlArrancar() {
        var env = new MockEnvironment().withProperty("prisaly.m7.zona-horaria", "Marte/Olympus");
        var error = assertThrows(IllegalStateException.class, () -> M7Configuration.zonaPresentacion(env));
        assertTrue(error.getMessage().contains("prisaly.m7.zona-horaria"), error.getMessage());
    }
}
