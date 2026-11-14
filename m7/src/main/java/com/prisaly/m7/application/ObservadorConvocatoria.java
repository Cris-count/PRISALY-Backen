package com.prisaly.m7.application;

import com.prisaly.m7.domain.ConvocatoriaNotificable;

/**
 * Patrón Observer. Quien implementa esto se entera de una convocatoria ya creada.
 * actualizar() corre en la misma transacción y no debe llamar a SMTP.
 */
public interface ObservadorConvocatoria {
    /** Procesamiento síncrono dentro de la misma transacción; sin efectos externos. */
    void actualizar(ConvocatoriaNotificable evento);
}
