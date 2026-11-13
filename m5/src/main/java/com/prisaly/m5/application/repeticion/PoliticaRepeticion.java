package com.prisaly.m5.application.repeticion;

/**
 * Habilitación de la repetición excepcional (PF-08 / DD-14).
 *
 * <p>Mientras PF-08 no esté ratificada, la única implementación productiva es
 * {@link RepeticionNoRatificada}, que nunca habilita. Sustituirla es parte de la activación.
 */
@FunctionalInterface
public interface PoliticaRepeticion {

    boolean autorizacionHabilitada();
}
