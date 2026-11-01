package com.prisaly.m3.infrastructure.persistence.jpa;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.annotations.Immutable;

/** Read-only representation of the complete BD-005.2 Caso row. */
@Entity
@Immutable
@Table(name = "\"Caso\"", schema = "public")
public class CasoJpaEntity {

    @Id
    @Column(name = "\"idCaso\"", nullable = false)
    private UUID idCaso;

    @Column(name = "\"idDocente\"", nullable = false)
    private UUID idDocente;

    @Column(name = "\"nombre\"", nullable = false, length = 150)
    private String nombre;

    @Column(name = "\"historia\"", columnDefinition = "text")
    private String historia;

    @Column(name = "\"desarrolloSituacional\"", columnDefinition = "text")
    private String desarrolloSituacional;

    @Column(name = "\"tiempoEstimadoSegundos\"", nullable = false)
    private int tiempoEstimadoSegundos;

    @Column(name = "\"estado\"", nullable = false, length = 20)
    private String estado;

    @Column(name = "\"activo\"", nullable = false)
    private boolean activo;

    @Column(name = "\"fechaCreacion\"", nullable = false)
    private Instant fechaCreacion;

    @Column(name = "\"fechaActualizacion\"")
    private Instant fechaActualizacion;

    @Column(name = "\"fechaBloqueoAcademico\"")
    private Instant fechaBloqueoAcademico;

    protected CasoJpaEntity() {
    }

    public UUID getIdCaso() { return idCaso; }
    public UUID getIdDocente() { return idDocente; }
    public String getNombre() { return nombre; }
    public String getHistoria() { return historia; }
    public String getDesarrolloSituacional() { return desarrolloSituacional; }
    public int getTiempoEstimadoSegundos() { return tiempoEstimadoSegundos; }
    public String getEstado() { return estado; }
    public boolean isActivo() { return activo; }
    public Instant getFechaCreacion() { return fechaCreacion; }
    public Instant getFechaActualizacion() { return fechaActualizacion; }
    public Instant getFechaBloqueoAcademico() { return fechaBloqueoAcademico; }
}
