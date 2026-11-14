package com.prisaly.m4.api;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Consulta recomendada para M5 en una petición estudiantil. Obtiene la identidad
 * exclusivamente de StudentAccessPort de M1, sin aceptar idEstudiante del cliente.
 * Mientras M1 deniegue acceso por DD-02, esta consulta también lo deniega.
 * Entregar contexto no autoriza iniciar/reanudar ni repetir un intento.
 */
public interface ProgramacionAutenticadaPort {
    Optional<ContextoProgramacion> consultarSesion(UUID sesionId);
    Optional<ContextoProgramacion> consultarConvocatoria(UUID convocatoriaId);

    /**
     * DC-BE-CIERRE-FUNCIONAL-09, fase 2: convocatorias del estudiante autenticado por M1. No recibe ningún
     * identificador del cliente. Las implementaciones que no lo soportan fallan cerradas.
     */
    default List<ContextoProgramacion> listarConvocatoriasPropias() {
        throw new ConsultaProgramacionNoDisponibleException();
    }
}
