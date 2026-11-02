package com.prisaly.m5.application;

import java.time.Instant;

/**
 * Resultado de iniciar o reanudar un intento.
 *
 * <p>{@code inicio} y {@code limiteTemporal} son los valores persistidos en
 * {@code "Intento"}. Al reanudar se devuelven tal como están: no se recalculan
 * ni se amplían.
 *
 * @param intento vista inmutable del intento, con sus respuestas persistidas
 * @param reanudado {@code true} si se devolvió el intento en curso existente;
 *     {@code false} si esta operación lo creó
 */
public record ResultadoInicio(ResumenIntento intento, Instant inicio, Instant limiteTemporal, boolean reanudado) {

    public ResultadoInicio {
        if (intento == null || inicio == null || limiteTemporal == null) {
            throw new DatoAplicacionInvalidoException("El resultado del inicio requiere el intento y sus tiempos");
        }
    }
}
