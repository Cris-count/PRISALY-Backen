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
 * por M4, sin leer tablas ni repositorios de M4. M4 lo publicó como
 * {@code com.prisaly.m4.api.ProgramacionParticipacionPort} (315746e, PR #2),
 * pendiente de conciliación e integración: recibe convocatoria y estudiante y
 * devuelve vacío tanto si no existe como si es ajena, así que esta firma
 * podría tener que cambiar. El titular se compara con el {@code studentId} que
 * entrega M1, nunca con un identificador enviado por el cliente. Hasta integrar
 * M4, el control de acceso no se conecta y la denegación se mantiene.
 */
public interface TitularidadIntentos {

    /** Titular del intento; vacío si el intento no existe o no tiene titular conocido. */
    Optional<IdentificadorEstudiante> titularDe(ReferenciaIntento intento);
}
