package com.prisaly.m4.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.lang.reflect.RecordComponent;
import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.Test;

/**
 * C1 (DC-CLAUDE-BE-COMP-03): {@code tiempoEstimadoCaso} es obligatorio y positivo, igual que
 * {@code Caso.tiempoEstimadoSegundos} en BD-005.2. El contrato no inventa valores por defecto
 * ni contiene política de ingreso tardío (RF-22).
 */
class ContextoProgramacionContratoTest {
    private static final Instant INICIO = Instant.parse("2026-11-05T14:00:00Z");

    private static ContextoProgramacion contexto(Duration tiempoEstimadoCaso) {
        return new ContextoProgramacion(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                UUID.randomUUID(), "SinIniciar", "Autorizado", Optional.empty(), "Publicado", true,
                INICIO, INICIO.plusSeconds(3600), Optional.empty(), Optional.empty(),
                Duration.ofMinutes(30), tiempoEstimadoCaso, INICIO);
    }

    @Test
    void conservaElTiempoEstimadoDelCasoSinTransformarlo() {
        assertEquals(Duration.ofSeconds(600), contexto(Duration.ofSeconds(600)).tiempoEstimadoCaso());
        assertEquals(Duration.ofSeconds(1), contexto(Duration.ofSeconds(1)).tiempoEstimadoCaso());
    }

    @Test
    void rechazaTiempoEstimadoNuloCeroONegativo() {
        assertThrows(NullPointerException.class, () -> contexto(null));
        assertThrows(IllegalArgumentException.class, () -> contexto(Duration.ZERO));
        assertThrows(IllegalArgumentException.class, () -> contexto(Duration.ofSeconds(-1)));
    }

    @Test
    void elComponenteVaTrasElMaximoDeParticipacionYAntesDeLaObservacion() {
        List<String> nombres = Arrays.stream(ContextoProgramacion.class.getRecordComponents())
                .map(RecordComponent::getName).toList();
        int posicion = nombres.indexOf("tiempoEstimadoCaso");
        assertEquals("tiempoMaximoParticipacion", nombres.get(posicion - 1));
        assertEquals("observadoEn", nombres.get(posicion + 1));
        assertEquals(Duration.class, ContextoProgramacion.class.getRecordComponents()[posicion].getType());
    }
}
