package com.prisaly.m5.application.inicio;

/**
 * Decisiones académicas sin ratificar que impiden completar un primer inicio.
 * No son estados ni reglas: solo identifican qué falta decidir.
 */
public enum DecisionPendiente {

    /** Condiciones para que un estudiante pueda iniciar (Dirección Técnica y M4). */
    ELEGIBILIDAD_ACADEMICA("la regla de elegibilidad académica"),

    /** Cálculo de {@code "Intento"."limiteTemporal"} (F3-04, pendiente de Dirección Técnica). */
    LIMITE_TEMPORAL("el cálculo del límite temporal del intento");

    private final String descripcion;

    DecisionPendiente(String descripcion) {
        this.descripcion = descripcion;
    }

    public String descripcion() {
        return descripcion;
    }
}
