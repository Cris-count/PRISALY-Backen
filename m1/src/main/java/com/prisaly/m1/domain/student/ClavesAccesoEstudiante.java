package com.prisaly.m1.domain.student;

/**
 * D-B5 (DC-DD02-B4-B5-01): independent K-COD (access codes) and K-SES (session tokens). Replaces the former single
 * 16-byte {@code ClaveHmacEstudiante} that signed both. Construction fails when the two sets share any key material.
 */
public record ClavesAccesoEstudiante(ConjuntoClavesHmac codigo, ConjuntoClavesHmac sesion) {

    public ClavesAccesoEstudiante {
        if (codigo == null || sesion == null) {
            throw new IllegalArgumentException("Faltan claves de acceso estudiantil");
        }
        if (codigo.compartenMaterial(sesion)) {
            throw new IllegalArgumentException("K-COD y K-SES deben ser claves independientes");
        }
    }
}
