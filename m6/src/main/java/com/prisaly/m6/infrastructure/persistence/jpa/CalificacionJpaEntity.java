package com.prisaly.m6.infrastructure.persistence.jpa;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.annotations.Formula;
import org.hibernate.annotations.Immutable;

/** Read-only physical row from official BD-005.2; no cross-module JPA associations. */
@Entity
@Immutable
@Table(name = "\"Calificacion\"", schema = "public")
public class CalificacionJpaEntity {
    @Id
    @Column(name = "\"idCalificacion\"", nullable = false)
    private UUID idCalificacion;

    @Column(name = "\"idIntento\"", nullable = false, unique = true)
    private UUID idIntento;

    @Column(name = "\"idRubrica\"", nullable = false)
    private UUID idRubrica;

    @Column(name = "\"valor\"", nullable = false, precision = 8, scale = 2)
    private BigDecimal valor;

    @Column(name = "\"fechaCalculo\"", nullable = false)
    private Instant fechaCalculo;

    // V001 enforces JSONB NOT NULL. JSON null reads as "null", not a Java null.
    // This is deliberately a formula: no JSON FormatMapper and no write mapping.
    @Formula("cast(\"detalleEvaluacion\" as text)")
    private String detalleEvaluacion;

    @Column(name = "\"versionPolitica\"", length = 50)
    private String versionPolitica;

    protected CalificacionJpaEntity() { }

    public UUID getIdCalificacion() { return idCalificacion; }
    public UUID getIdIntento() { return idIntento; }
    public UUID getIdRubrica() { return idRubrica; }
    public BigDecimal getValor() { return valor; }
    public Instant getFechaCalculo() { return fechaCalculo; }
    public String getDetalleEvaluacion() { return detalleEvaluacion; }
    public String getVersionPolitica() { return versionPolitica; }
}
