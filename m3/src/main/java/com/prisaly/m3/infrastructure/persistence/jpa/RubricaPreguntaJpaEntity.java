package com.prisaly.m3.infrastructure.persistence.jpa;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.util.UUID;
import org.hibernate.annotations.Immutable;

/** Read-only association row, without a domain criterion conversion or implicit order. */
@Entity
@Immutable
@Table(name = "\"RubricaPregunta\"", schema = "public")
public class RubricaPreguntaJpaEntity {

    @Id
    @Column(name = "\"idRubricaPregunta\"", nullable = false)
    private UUID idRubricaPregunta;

    @Column(name = "\"idRubrica\"", nullable = false)
    private UUID idRubrica;

    @Column(name = "\"idPregunta\"", nullable = false)
    private UUID idPregunta;

    @Column(name = "\"valorPregunta\"", nullable = false, precision = 8, scale = 2)
    private BigDecimal valorPregunta;

    @Column(name = "\"criterio\"", columnDefinition = "text")
    private String criterio;

    protected RubricaPreguntaJpaEntity() {
    }

    public UUID getIdRubricaPregunta() { return idRubricaPregunta; }
    public UUID getIdRubrica() { return idRubrica; }
    public UUID getIdPregunta() { return idPregunta; }
    public BigDecimal getValorPregunta() { return valorPregunta; }
    public String getCriterio() { return criterio; }
}
