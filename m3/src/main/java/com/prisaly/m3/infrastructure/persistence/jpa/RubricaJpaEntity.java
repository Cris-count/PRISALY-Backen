package com.prisaly.m3.infrastructure.persistence.jpa;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.annotations.Formula;
import org.hibernate.annotations.Immutable;

/** Read-only BD-005.2 row. JSON is retained as opaque JSON text, not an academic model. */
@Entity
@Immutable
@Table(name = "\"Rubrica\"", schema = "public")
public class RubricaJpaEntity {

    @Id
    @Column(name = "\"idRubrica\"", nullable = false)
    private UUID idRubrica;

    @Column(name = "\"idCaso\"", nullable = false)
    private UUID idCaso;

    @Column(name = "\"nombre\"", nullable = false, length = 150)
    private String nombre;

    @Column(name = "\"descripcion\"", columnDefinition = "text")
    private String descripcion;

    @Column(name = "\"puntajeTotal\"", precision = 8, scale = 2)
    private BigDecimal puntajeTotal;

    // Read PostgreSQL's JSONB text directly, without a JSON FormatMapper or
    // serializing the JSON document as a JSON string. SQL NULL remains null.
    @Formula("cast(\"definicionEvaluacion\" as text)")
    private String definicionEvaluacion;

    @Column(name = "\"fechaCreacion\"", nullable = false)
    private Instant fechaCreacion;

    protected RubricaJpaEntity() {
    }

    public UUID getIdRubrica() { return idRubrica; }
    public UUID getIdCaso() { return idCaso; }
    public String getNombre() { return nombre; }
    public String getDescripcion() { return descripcion; }
    public BigDecimal getPuntajeTotal() { return puntajeTotal; }
    public String getDefinicionEvaluacion() { return definicionEvaluacion; }
    public Instant getFechaCreacion() { return fechaCreacion; }
}
