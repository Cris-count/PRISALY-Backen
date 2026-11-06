package com.prisaly.m5.application;

/**
 * Referencia a una sesión académica de M4 ({@code "Sesion"."idSesion"}) tal como llega en la ruta.
 *
 * <p>Es solo un localizador: no prueba que exista una convocatoria del estudiante en esa sesión.
 * La convocatoria se resuelve con el contrato de M4 y la identidad de M1 en
 * {@code ControlAccesoIntentos} (D01).
 */
public record ReferenciaSesion(String valor) {

    public ReferenciaSesion {
        if (valor == null || valor.isBlank()) {
            throw new DatoAplicacionInvalidoException("La referencia de la sesión es obligatoria");
        }
    }
}
