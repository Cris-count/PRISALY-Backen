package com.prisaly.m7.infrastructure;

import com.prisaly.m7.application.ContextoConvocatoriaPort;
import com.prisaly.m7.domain.ConvocatoriaNotificable;
import jakarta.persistence.EntityManager;
import java.util.UUID;

/** Solo SQL del esquema público; no usa repositorios ni entidades internas de M4/M1. */
public class ContextoConvocatoriaJpaAdapter implements ContextoConvocatoriaPort {
    private final EntityManager em;

    public ContextoConvocatoriaJpaAdapter(EntityManager em) { this.em = em; }

    @Override
    public ConvocatoriaNotificable resolver(UUID sesionId, UUID estudianteId) {
        // M4 usa save(), que puede dejar el INSERT pendiente. Hacerlo visible sin commit.
        em.flush();
        var filas = em.createNativeQuery("""
                SELECT c."idConvocatoria", e."idUsuario"
                FROM "Convocatoria" c
                JOIN "Estudiante" e ON e."idEstudiante" = c."idEstudiante"
                JOIN "Usuario" u ON u."idUsuario" = e."idUsuario"
                WHERE c."idSesion" = :sesion AND c."idEstudiante" = :estudiante
                  AND c."fechaRetiro" IS NULL
                """).setParameter("sesion", sesionId).setParameter("estudiante", estudianteId)
                .getResultList();
        if (filas.size() != 1) throw new IllegalStateException("Convocatoria destinataria no disponible");
        var fila = (Object[]) filas.getFirst();
        return new ConvocatoriaNotificable((UUID) fila[0], sesionId, estudianteId, (UUID) fila[1]);
    }
}
