package com.prisaly.m5.infrastructure.persistencia;

import java.time.OffsetDateTime;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * Fila de {@code "Respuesta"} (BD-005.2, V001). La relación con el intento
 * se mantiene por la columna {@code idIntento}; la base garantiza una
 * respuesta por (intento, pregunta) con {@code uq_respuesta_idx1}, que la
 * opción pertenezca a la pregunta (FK compuesta) y que la pregunta sea del
 * caso de la participación (trigger {@code tr_respuesta_caso}).
 */
@Entity
@Table(name = "Respuesta")
public class RespuestaEntity {

    @Id
    @Column(name = "idRespuesta", nullable = false, updatable = false)
    private UUID idRespuesta;

    @Column(name = "idIntento", nullable = false, updatable = false)
    private UUID idIntento;

    @Column(name = "idPregunta", nullable = false, updatable = false)
    private UUID idPregunta;

    @Column(name = "idOpcionSeleccionada", nullable = false)
    private UUID idOpcionSeleccionada;

    @Column(name = "revision", nullable = false)
    private int revision;

    @Column(name = "fechaActualizacion", nullable = false)
    private OffsetDateTime fechaActualizacion;

    protected RespuestaEntity() {
    }

    static RespuestaEntity nueva(UUID idIntento, UUID idPregunta, UUID idOpcion, OffsetDateTime ahora) {
        RespuestaEntity entidad = new RespuestaEntity();
        entidad.idRespuesta = UUID.randomUUID();
        entidad.idIntento = idIntento;
        entidad.idPregunta = idPregunta;
        entidad.idOpcionSeleccionada = idOpcion;
        entidad.revision = 1;
        entidad.fechaActualizacion = ahora;
        return entidad;
    }

    /** Reemplazo provisional de la opción: incrementa {@code revision}. */
    void reemplazarOpcion(UUID idOpcion, OffsetDateTime ahora) {
        idOpcionSeleccionada = idOpcion;
        revision++;
        fechaActualizacion = ahora;
    }

    public UUID getIdRespuesta() {
        return idRespuesta;
    }

    public UUID getIdIntento() {
        return idIntento;
    }

    public UUID getIdPregunta() {
        return idPregunta;
    }

    public UUID getIdOpcionSeleccionada() {
        return idOpcionSeleccionada;
    }

    public int getRevision() {
        return revision;
    }

    public OffsetDateTime getFechaActualizacion() {
        return fechaActualizacion;
    }
}
