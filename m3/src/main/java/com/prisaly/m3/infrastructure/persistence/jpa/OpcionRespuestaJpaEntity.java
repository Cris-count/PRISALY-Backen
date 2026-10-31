package com.prisaly.m3.infrastructure.persistence.jpa;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.util.UUID;
import org.hibernate.annotations.Immutable;

/** Read-only mapping of the BD-005.2 OpcionRespuesta row; foreign keys remain scalar UUIDs. */
@Entity
@Immutable
@Table(name = "\"OpcionRespuesta\"", schema = "public")
public class OpcionRespuestaJpaEntity {

    @Id
    @Column(name = "\"idOpcion\"", nullable = false)
    private UUID idOpcion;

    @Column(name = "\"idPregunta\"", nullable = false)
    private UUID idPregunta;

    @Column(name = "\"texto\"", nullable = false, columnDefinition = "text")
    private String texto;

    @Column(name = "\"esCorrecta\"", nullable = false)
    private boolean esCorrecta;

    @Column(name = "\"explicacion\"", columnDefinition = "text")
    private String explicacion;

    @Column(name = "\"orden\"", nullable = false)
    private int orden;

    protected OpcionRespuestaJpaEntity() {
    }

    public UUID getIdOpcion() { return idOpcion; }

    public UUID getIdPregunta() { return idPregunta; }

    public String getTexto() { return texto; }

    public boolean isEsCorrecta() { return esCorrecta; }

    public String getExplicacion() { return explicacion; }

    public int getOrden() { return orden; }
}
