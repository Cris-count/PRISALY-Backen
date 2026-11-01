package com.prisaly.m5.application.port;

import java.util.Optional;

import com.prisaly.m5.application.ReferenciaIntento;
import com.prisaly.m5.domain.intento.Intento;

/**
 * Puerto de salida para cargar y guardar intentos.
 *
 * <p>Las respuestas pertenecen al agregado {@link Intento}: se cargan y se
 * guardan con él, por lo que no se define un puerto separado para ellas.
 *
 * <p>Requisitos para el futuro adaptador PostgreSQL (pendientes de BD-005.2):
 * <ul>
 *   <li>{@link #guardar} debe persistir de forma atómica el estado, el motivo
 *       de finalización y todas las respuestas del intento, dentro de la
 *       transacción del caso de uso que lo invoca.</li>
 *   <li>Debe impedir la pérdida de actualizaciones entre solicitudes
 *       concurrentes sobre el mismo intento (por ejemplo, bloqueo pesimista al
 *       cargar o control optimista por versión). La estrategia se decidirá con
 *       el modelo físico; hoy {@code "Intento"} no tiene columna de versión.</li>
 *   <li>No debe convertir en éxito un intento inexistente.</li>
 * </ul>
 * No existe todavía ninguna implementación de producción.
 */
public interface RepositorioIntentos {

    /** Busca un intento por su referencia; vacío si no existe. */
    Optional<Intento> buscar(ReferenciaIntento referencia);

    /** Persiste el estado actual del intento identificado por la referencia. */
    void guardar(ReferenciaIntento referencia, Intento intento);
}
