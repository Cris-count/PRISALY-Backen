package com.prisaly.config.dc92;

import static org.junit.jupiter.api.Assertions.*;

import java.util.UUID;
import org.junit.jupiter.api.Test;

/** DC-92: the literal DC-90 derivations of DD-13 / RF-21 (participation state) and DD-04 / DD-09 / DD-11 (evaluation). */
class VistaParticipacionDc92Test {

    private static VistaParticipacionDc92.Hechos h(boolean retirado, int intentos, boolean enCurso, String ausencia,
            boolean sesionFinalizada, UUID seleccion, boolean considerado, boolean calificada, boolean publicada) {
        return new VistaParticipacionDc92.Hechos(retirado, intentos, enCurso, ausencia, sesionFinalizada, seleccion,
                considerado, calificada, publicada);
    }

    @Test
    void estadosDeParticipacionDd13YRetiroRf21() {
        assertEquals("CONVOCADO", VistaParticipacionDc92.estadoParticipacion(h(false, 0, false, null, false, null, false, false, false)));
        assertEquals("AUSENTE_PENDIENTE_REVISION", VistaParticipacionDc92.estadoParticipacion(h(false, 0, false, null, true, null, false, false, false)));
        assertEquals("AUSENCIA_JUSTIFICADA", VistaParticipacionDc92.estadoParticipacion(h(false, 0, false, "AUSENCIA_JUSTIFICADA", true, null, false, false, false)));
        assertEquals("AUSENCIA_NO_JUSTIFICADA", VistaParticipacionDc92.estadoParticipacion(h(false, 0, false, "AUSENCIA_NO_JUSTIFICADA", true, null, false, false, false)));
        assertEquals("PARTICIPACION_INICIADA", VistaParticipacionDc92.estadoParticipacion(h(false, 1, true, null, false, null, true, false, false)));
        assertEquals("PARTICIPACION_FINALIZADA", VistaParticipacionDc92.estadoParticipacion(h(false, 2, false, null, true, null, false, false, false)));
        assertEquals("RETIRADO", VistaParticipacionDc92.estadoParticipacion(h(true, 1, false, null, true, null, true, true, true)),
                "withdrawal prevails and keeps the history");
    }

    @Test
    void ausenciaNuncaProduceCalificacion() {
        assertEquals("SIN_INTENTOS", VistaParticipacionDc92.estadoEvaluacion(h(false, 0, false, "AUSENCIA_NO_JUSTIFICADA", true, null, false, false, false)));
    }

    @Test
    void estadoDeEvaluacionSoloPublicadaConNotaLiberadaYConIntentoConsiderado() {
        assertEquals("SELECCION_PENDIENTE", VistaParticipacionDc92.estadoEvaluacion(h(false, 2, false, null, true, null, false, true, true)),
                "two attempts without the teacher's selection: no grade is shown");
        assertEquals("SIN_EVALUAR", VistaParticipacionDc92.estadoEvaluacion(h(false, 1, false, null, true, null, true, false, false)));
        assertEquals("EVALUADA_NO_PUBLICADA", VistaParticipacionDc92.estadoEvaluacion(h(false, 1, false, null, true, null, true, true, false)));
        assertEquals("PUBLICADA", VistaParticipacionDc92.estadoEvaluacion(h(false, 2, false, null, true, UUID.randomUUID(), true, true, true)));
    }
}
