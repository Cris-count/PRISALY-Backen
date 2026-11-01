package com.prisaly.m7.infrastructure;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

/** Mapeo para ddl-auto=validate. Las escrituras idempotentes usan SQL PostgreSQL parametrizado. */
@Entity
@Table(name = "\"Aviso\"", schema = "public")
public class AvisoEntity {
    @Id @Column(name = "\"idAviso\"", nullable = false)
    private UUID id;
    @Column(name = "\"idUsuarioDestino\"", nullable = false)
    private UUID usuarioDestino;
    @Column(name = "\"idParticipacion\"")
    private UUID participacion;
    @Column(name = "\"tipo\"", nullable = false, length = 50)
    private String tipo;
    @Column(name = "\"contenido\"", nullable = false, columnDefinition = "text")
    private String contenido;
    @Column(name = "\"claveEvento\"", nullable = false, length = 150)
    private String claveEvento;
    @Column(name = "\"fechaCreacion\"", nullable = false)
    private Instant fechaCreacion;
    @Column(name = "\"fechaLectura\"")
    private Instant fechaLectura;

    protected AvisoEntity() {}
}
