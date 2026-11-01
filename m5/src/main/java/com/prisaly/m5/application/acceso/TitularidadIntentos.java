package com.prisaly.m5.application.acceso;

import java.util.Optional;

import com.prisaly.m5.application.ReferenciaIntento;

/**
 * Puerto PROVISIONAL de M5 (lado consumidor): el estudiante titular de un
 * intento.
 *
 * <p>No tiene implementación de producción. En BD-005.2 el titular se llega
 * por Intento → Participacion → Convocatoria."idEstudiante", y Convocatoria
 * pertenece a M4; la implementación deberá usar el contrato Java que M4
 * publique, no leer sus tablas desde M5.
 */
public interface TitularidadIntentos {

    /** Titular del intento; vacío si el intento no existe o no tiene titular conocido. */
    Optional<IdentificadorEstudiante> titularDe(ReferenciaIntento intento);
}
