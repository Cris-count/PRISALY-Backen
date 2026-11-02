package com.prisaly.m5.infrastructure.persistencia;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;

import com.prisaly.m5.application.ReferenciaConvocatoria;
import com.prisaly.m5.application.inicio.ElegibilidadInicio;
import com.prisaly.m5.application.inicio.LimiteTemporalInicio;

/**
 * SOLO PRUEBAS. Dobles de las políticas pendientes para ejercitar el mecanismo
 * de primer inicio contra PostgreSQL. No son reglas académicas ni una propuesta
 * de cálculo: el ensamblado de producción usa las implementaciones
 * "no ratificadas", que nunca autorizan.
 */
final class PoliticasInicioDePrueba {

    private PoliticasInicioDePrueba() {
    }

    /** Autoriza siempre: solo para probar la mecánica de creación. */
    static ElegibilidadInicio elegibleSiempre() {
        return convocatoria -> ElegibilidadInicio.Resultado.ELEGIBLE;
    }

    /** Límite fijo de prueba a partir del inicio recibido. */
    static LimiteTemporalInicio limiteFijo(Duration duracion) {
        return (convocatoria, inicio) -> Optional.of(inicio.plus(duracion));
    }

    /** Límite inválido ({@code = inicio}): fuerza a {@code ck_intento_tiempos} a rechazar el intento. */
    static LimiteTemporalInicio limiteInvalido() {
        return (ReferenciaConvocatoria convocatoria, Instant inicio) -> Optional.of(inicio);
    }
}
