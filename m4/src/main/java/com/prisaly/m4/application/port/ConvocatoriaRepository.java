package com.prisaly.m4.application.port;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.prisaly.m4.domain.model.Convocatoria;

/**
 * Puerto de persistencia para convocatorias estudiantiles.
 */
public interface ConvocatoriaRepository {

    Convocatoria guardar(Convocatoria convocatoria);

    Optional<Convocatoria> buscarPorId(UUID id);

    List<Convocatoria> listarPorSesion(UUID sesionId);

    /**
     * Verifica si un estudiante ya fue convocado a una sesión específica.
     */
    boolean existeConvocatoria(UUID sesionId, UUID estudianteId);

    /**
     * Verifica si un estudiante está convocado a la sesión indicada.
     * Usado por M5 para validar acceso.
     */
    boolean estaConvocado(UUID sesionId, UUID estudianteId);
}
