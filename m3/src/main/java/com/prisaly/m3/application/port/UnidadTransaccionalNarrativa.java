package com.prisaly.m3.application.port;

import java.util.function.Supplier;

/** Ejecuta un caso de uso narrativo en una única transacción de escritura (la provee la infraestructura). */
@FunctionalInterface
public interface UnidadTransaccionalNarrativa {

    <T> T ejecutar(Supplier<T> casoDeUso);
}
