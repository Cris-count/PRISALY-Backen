package com.prisaly.m3.infrastructure.persistence.jpa;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.util.UUID;
import org.hibernate.annotations.Immutable;

/** Read-only mapping of the BD-005.2 Escenario row; foreign keys remain scalar UUIDs. */
@Entity
@Immutable
@Table(name = "\"Escenario\"", schema = "public")
public class EscenarioJpaEntity {

    @Id
    @Column(name = "\"idEscenario\"", nullable = false)
    private UUID idEscenario;

    @Column(name = "\"idCaso\"", nullable = false)
    private UUID idCaso;

    @Column(name = "\"titulo\"", length = 200)
    private String titulo;

    @Column(name = "\"contexto\"", columnDefinition = "text")
    private String contexto;

    @Column(name = "\"instrucciones\"", columnDefinition = "text")
    private String instrucciones;

    @Column(name = "\"orden\"", nullable = false)
    private int orden;

    @Column(name = "\"activo\"", nullable = false)
    private boolean activo;

    protected EscenarioJpaEntity() {
    }

    public UUID getIdEscenario() { return idEscenario; }

    public UUID getIdCaso() { return idCaso; }

    public String getTitulo() { return titulo; }

    public String getContexto() { return contexto; }

    public String getInstrucciones() { return instrucciones; }

    public int getOrden() { return orden; }

    public boolean isActivo() { return activo; }
}
