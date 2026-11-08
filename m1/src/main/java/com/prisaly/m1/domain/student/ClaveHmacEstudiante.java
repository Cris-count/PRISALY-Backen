package com.prisaly.m1.domain.student;

/** Clave de servidor para firmar códigos y sesiones. No es una credencial de estudiante. */
public record ClaveHmacEstudiante(byte[] valor) {

    public ClaveHmacEstudiante {
        if (valor == null || valor.length < 16) {
            throw new IllegalArgumentException("La clave HMAC de acceso estudiantil es insuficiente");
        }
        valor = FirmasEstudiante.copiar(valor);
    }
}
