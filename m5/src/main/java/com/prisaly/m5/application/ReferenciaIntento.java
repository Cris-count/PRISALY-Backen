package com.prisaly.m5.application;

/**
 * Referencia técnica mínima a un intento, usada solo para localizarlo a
 * través de {@link RepositorioIntentos}.
 *
 * <p>Provisional: su correspondencia con BD-005.2 está pendiente. La rama de
 * M1 propone {@code "Intento"."idIntento" uuid}, pero esas migraciones aún no
 * están integradas en develop; por eso el valor se mantiene opaco y M5 no lo
 * interpreta.
 */
public record ReferenciaIntento(String valor) {

    public ReferenciaIntento {
        if (valor == null || valor.isBlank()) {
            throw new DatoAplicacionInvalidoException("La referencia del intento es obligatoria");
        }
    }
}
