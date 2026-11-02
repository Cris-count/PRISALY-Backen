package com.prisaly.m5.application.inicio;

import com.prisaly.m5.application.ReferenciaConvocatoria;

/**
 * Puerto de M5 (lado consumidor): ¿puede el titular de la convocatoria
 * iniciar su primer intento?
 *
 * <p>Solo se consulta en el primer inicio, nunca al reanudar. Se invoca
 * después de verificar identidad (M1) y titularidad (M4), dentro de la
 * transacción del caso de uso.
 *
 * <p>La regla académica está PENDIENTE. La única implementación de producción
 * es {@link ElegibilidadNoRatificada}, que nunca autoriza. Una implementación
 * futura deberá basarse en una regla ratificada y en contratos de M4.
 */
public interface ElegibilidadInicio {

    /** Resultado de la evaluación. */
    enum Resultado {
        ELEGIBLE,
        NO_ELEGIBLE,
        /** No hay regla ratificada: no puede determinarse. */
        REGLA_NO_RATIFICADA
    }

    /** @return el resultado; {@code null} se trata como {@link Resultado#REGLA_NO_RATIFICADA} */
    Resultado evaluar(ReferenciaConvocatoria convocatoria);
}
