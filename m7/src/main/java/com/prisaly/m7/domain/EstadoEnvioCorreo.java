package com.prisaly.m7.domain;

/** Ratified EnvioCorreo.estadoEnvio catalog (D1). No DB CHECK until V010 is authorized. */
public final class EstadoEnvioCorreo {

    public static final String PENDIENTE = "Pendiente";
    public static final String ENVIADO = "Enviado";
    public static final String FALLIDO = "Fallido";

    private EstadoEnvioCorreo() {}
}
