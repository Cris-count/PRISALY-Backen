package com.prisaly.m3.domain.model;

import java.util.List;
import java.time.Instant;

/**
 * Single-choice question belonging to a scenario. Null orden/fechaCreacion denote
 * unassigned transient metadata; both are required for a complete PostgreSQL row.
 * Supplied dates and editing states are transported without inferred transitions.
 */
public record Pregunta(
        String id, String enunciado, List<OpcionRespuesta> opciones, String textoAyuda,
        String estadoEdicion, boolean visible, Integer orden,
        Instant fechaCreacion, Instant fechaActualizacion) {

    public Pregunta {
        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException("Pregunta.id is required");
        }
        if (enunciado == null || enunciado.isBlank()) {
            throw new IllegalArgumentException("Pregunta.enunciado is required");
        }
        if (opciones == null) {
            throw new IllegalArgumentException("Pregunta.opciones is required");
        }
        if (!"Borrador".equals(estadoEdicion) && !"Lista".equals(estadoEdicion)) {
            throw new IllegalArgumentException("Pregunta.estadoEdicion must be Borrador or Lista");
        }
        if (orden != null && orden <= 0) {
            throw new IllegalArgumentException("Pregunta.orden must be greater than zero");
        }
        opciones = List.copyOf(opciones);
    }

    /**
     * Transient compatibility: physical order and creation date are unassigned,
     * not zero/current time. State and visibility follow V001 defaults.
     */
    public Pregunta(String id, String enunciado, List<OpcionRespuesta> opciones) {
        this(id, enunciado, opciones, null, "Borrador", true, null, null, null);
    }

    public long correctOptionCount() {
        return opciones.stream().filter(OpcionRespuesta::esCorrecta).count();
    }
}
