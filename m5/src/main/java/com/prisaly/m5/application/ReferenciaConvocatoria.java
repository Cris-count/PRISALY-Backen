package com.prisaly.m5.application;

/**
 * Referencia a una convocatoria de M4 ({@code "Convocatoria"."idConvocatoria"}).
 *
 * <p>Es solo un localizador: no prueba que la convocatoria exista ni que sea
 * del estudiante. Lo comprueba {@code ControlAccesoIntentos} con el contrato
 * de M4 y la identidad de M1 antes de iniciar o reanudar.
 */
public record ReferenciaConvocatoria(String valor) {

    public ReferenciaConvocatoria {
        if (valor == null || valor.isBlank()) {
            throw new DatoAplicacionInvalidoException("La referencia de la convocatoria es obligatoria");
        }
    }
}
