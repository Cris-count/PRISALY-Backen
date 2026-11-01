package com.prisaly.m4.api;

/** Fallo de proveedor/esquema. Nunca se transforma en contexto válido o permiso. */
public class ConsultaProgramacionNoDisponibleException extends RuntimeException {
    public static final String CODIGO = "M4-CONSULTA-PROGRAMACION-NO-DISPONIBLE";

    public ConsultaProgramacionNoDisponibleException() {
        super("La consulta de programación BD-005.2 no está disponible");
    }

    public ConsultaProgramacionNoDisponibleException(Throwable cause) {
        super("La consulta de programación BD-005.2 no está disponible", cause);
    }
}
