package com.prisaly.m5.application.inicio;

import com.prisaly.m5.application.ReferenciaConvocatoria;

/**
 * Implementación de producción mientras la elegibilidad académica siga
 * pendiente: nunca autoriza un primer inicio. No es una regla académica.
 */
public final class ElegibilidadNoRatificada implements ElegibilidadInicio {

    @Override
    public Resultado evaluar(ReferenciaConvocatoria convocatoria) {
        return Resultado.REGLA_NO_RATIFICADA;
    }
}
