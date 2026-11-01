package com.prisaly.m7.infrastructure;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/** Mapeo para ddl-auto=validate. Escrituras idempotentes usan SQL nativo. */
@Entity
@Table(name = "\"EnvioCorreo\"", schema = "public")
public class EnvioCorreoEntity {

    @Id
    @Column(name = "\"idEnvioCorreo\"", nullable = false)
    private UUID id;

    @Column(name = "\"idUsuarioDestino\"", nullable = false)
    private UUID usuarioDestino;

    @Column(name = "\"idConvocatoria\"")
    private UUID convocatoria;

    @Column(name = "\"correoDestino\"", nullable = false, length = 255)
    private String correoDestino;

    @Column(name = "\"asunto\"", nullable = false, length = 255)
    private String asunto;

    @Column(name = "\"contenido\"", nullable = false, columnDefinition = "text")
    private String contenido;

    @Column(name = "\"estadoEnvio\"", nullable = false, length = 25)
    private String estadoEnvio;

    @Column(name = "\"numeroIntentos\"", nullable = false)
    private int numeroIntentos;

    @Column(name = "\"claveIdempotencia\"", nullable = false, length = 150)
    private String claveIdempotencia;

    @Column(name = "\"ultimoError\"", columnDefinition = "text")
    private String ultimoError;

    @Column(name = "\"fechaCreacion\"", nullable = false)
    private Instant fechaCreacion;

    @Column(name = "\"fechaUltimoIntento\"")
    private Instant fechaUltimoIntento;

    @Column(name = "\"proximoReintento\"")
    private Instant proximoReintento;

    @Column(name = "\"fechaEnvio\"")
    private Instant fechaEnvio;

    protected EnvioCorreoEntity() {}
}
