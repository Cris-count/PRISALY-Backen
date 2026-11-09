package com.prisaly.m7.infrastructure;

import com.prisaly.m7.application.ContextoConvocatoriaPort;
import com.prisaly.m7.domain.ConvocatoriaNotificable;
import jakarta.persistence.EntityManager;
import java.util.Objects;
import java.util.UUID;

/**
 * Resuelve convocatoria + estudiante→usuario + correo vía SQL del esquema público.
 * No usa repositorios ni entidades internas de M4/M1.
 */
public class ContextoConvocatoriaJpaAdapter implements ContextoConvocatoriaPort {

    private final EntityManager em;

    public ContextoConvocatoriaJpaAdapter(EntityManager em) {
        this.em = Objects.requireNonNull(em, "em");
    }

    @Override
    public ConvocatoriaNotificable resolver(UUID sesionId, UUID estudianteId) {
        em.flush();
        var filas = em.createNativeQuery(
                        """
                        SELECT c."idConvocatoria", e."idUsuario", u."correo",
                               ca."nombre", s."nombreEspacioAcademico",
                               to_char(s."inicioPrevisto" AT TIME ZONE 'UTC', 'YYYY-MM-DD HH24:MI'),
                               to_char(s."finPrevisto" AT TIME ZONE 'UTC', 'YYYY-MM-DD HH24:MI'),
                               s."mensajePersonalizado"
                        FROM "Convocatoria" c
                        JOIN "Sesion" s ON s."idSesion" = c."idSesion"
                        JOIN "Caso" ca ON ca."idCaso" = s."idCaso"
                        JOIN "Estudiante" e ON e."idEstudiante" = c."idEstudiante"
                        JOIN "Usuario" u ON u."idUsuario" = e."idUsuario"
                        WHERE c."idSesion" = :sesion AND c."idEstudiante" = :estudiante
                          AND c."fechaRetiro" IS NULL
                        """)
                .setParameter("sesion", sesionId)
                .setParameter("estudiante", estudianteId)
                .getResultList();
        if (filas.size() != 1) {
            throw new IllegalStateException("Convocatoria destinataria no disponible");
        }
        var fila = (Object[]) filas.getFirst();
        return new ConvocatoriaNotificable(
                (UUID) fila[0], sesionId, estudianteId, (UUID) fila[1], (String) fila[2],
                (String) fila[3], (String) fila[4], (String) fila[5], (String) fila[6], (String) fila[7]);
    }
}
