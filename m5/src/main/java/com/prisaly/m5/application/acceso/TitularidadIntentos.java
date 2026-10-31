package com.prisaly.m5.application.acceso;

import java.util.Optional;

import com.prisaly.m5.application.ReferenciaIntento;

/**
 * Puerto PROVISIONAL de M5 (lado consumidor): el estudiante titular de un
 * intento.
 *
 * <p>No tiene implementación de producción. En BD-005.2 el titular se llega
 * por Intento."idParticipacion" → Participacion."idConvocatoria" (ambas
 * tablas de M5) → Convocatoria."idEstudiante" (tabla de M4). M5 puede
 * resolver su propio tramo; el último paso requiere un contrato Java publicado
 * por M4 (convocatoria → estudiante), sin leer tablas ni repositorios de M4.
 * El titular se compara con el {@code studentId} que entrega M1, nunca con un
 * identificador enviado por el cliente. Mientras falte el contrato de M4, el
 * control de acceso no puede conectarse y la denegación se mantiene.
 */
public interface TitularidadIntentos {

    /** Titular del intento; vacío si el intento no existe o no tiene titular conocido. */
    Optional<IdentificadorEstudiante> titularDe(ReferenciaIntento intento);
}
