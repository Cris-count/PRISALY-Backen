package com.prisaly.m5.application.repeticion;

/** Política por defecto: PF-08 sin ratificar, ninguna repetición se autoriza. */
public final class RepeticionNoRatificada implements PoliticaRepeticion {

    @Override
    public boolean autorizacionHabilitada() {
        return false;
    }
}
