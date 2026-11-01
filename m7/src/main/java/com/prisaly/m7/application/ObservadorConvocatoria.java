package com.prisaly.m7.application;

import com.prisaly.m7.domain.ConvocatoriaNotificable;

public interface ObservadorConvocatoria {
    /** Procesamiento síncrono dentro de la misma transacción; sin efectos externos. */
    void actualizar(ConvocatoriaNotificable evento);
}
