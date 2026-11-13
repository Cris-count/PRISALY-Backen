package com.prisaly.m5.application;

public final class ContenidoIntentoNoDisponibleException extends RuntimeException {
    public ContenidoIntentoNoDisponibleException() { super("Contenido académico no disponible para esta lectura"); }
}
