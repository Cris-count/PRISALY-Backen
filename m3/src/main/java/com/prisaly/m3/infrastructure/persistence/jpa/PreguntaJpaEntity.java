package com.prisaly.m3.infrastructure.persistence.jpa;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.annotations.Immutable;

/** Read-only mapping of the BD-005.2 Pregunta row; foreign keys remain scalar UUIDs. */
@Entity
@Immutable
@Table(name = "\"Pregunta\"", schema = "public")
public class PreguntaJpaEntity {

    @Id
    @Column(name = "\"idPregunta\"", nullable = false)
    private UUID idPregunta;

    @Column(name = "\"idEscenario\"", nullable = false)
    private UUID idEscenario;

    @Column(name = "\"enunciado\"", nullable = false, columnDefinition = "text")
    private String enunciado;

    @Column(name = "\"textoAyuda\"", columnDefinition = "text")
    private String textoAyuda;

    @Column(name = "\"estadoEdicion\"", nullable = false, length = 20)
    private String estadoEdicion;

    @Column(name = "\"visible\"", nullable = false)
    private boolean visible;

    @Column(name = "\"orden\"", nullable = false)
    private int orden;

    @Column(name = "\"fechaCreacion\"", nullable = false)
    private Instant fechaCreacion;

    @Column(name = "\"fechaActualizacion\"")
    private Instant fechaActualizacion;

    protected PreguntaJpaEntity() {
    }

    public UUID getIdPregunta() { return idPregunta; }

    public UUID getIdEscenario() { return idEscenario; }

    public String getEnunciado() { return enunciado; }

    public String getTextoAyuda() { return textoAyuda; }

    public String getEstadoEdicion() { return estadoEdicion; }

    public boolean isVisible() { return visible; }

    public int getOrden() { return orden; }

    public Instant getFechaCreacion() { return fechaCreacion; }

    public Instant getFechaActualizacion() { return fechaActualizacion; }
}
