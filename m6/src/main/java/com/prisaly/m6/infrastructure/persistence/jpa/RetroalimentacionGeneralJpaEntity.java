package com.prisaly.m6.infrastructure.persistence.jpa;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.annotations.Immutable;

/** Read-only mapping; @Immutable does not establish an SQL feedback lifecycle policy. */
@Entity
@Immutable
@Table(name = "\"RetroalimentacionGeneral\"", schema = "public")
public class RetroalimentacionGeneralJpaEntity {
    @Id
    @Column(name = "\"idRetroalimentacion\"", nullable = false)
    private UUID idRetroalimentacion;

    @Column(name = "\"idParticipacion\"", nullable = false, unique = true)
    private UUID idParticipacion;

    @Column(name = "\"idDocente\"", nullable = false)
    private UUID idDocente;

    @Column(name = "\"contenido\"", nullable = false, columnDefinition = "text")
    private String contenido;

    @Column(name = "\"fechaRegistro\"", nullable = false)
    private Instant fechaRegistro;

    protected RetroalimentacionGeneralJpaEntity() { }

    public UUID getIdRetroalimentacion() { return idRetroalimentacion; }
    public UUID getIdParticipacion() { return idParticipacion; }
    public UUID getIdDocente() { return idDocente; }
    public String getContenido() { return contenido; }
    public Instant getFechaRegistro() { return fechaRegistro; }
}
