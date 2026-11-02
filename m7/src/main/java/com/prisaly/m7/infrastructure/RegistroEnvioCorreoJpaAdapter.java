package com.prisaly.m7.infrastructure;

import com.prisaly.m7.application.RegistroEnvioCorreoPort;
import com.prisaly.m7.domain.EnvioCorreoConvocatoria;
import com.prisaly.m7.domain.EstadoEnvioCorreo;
import jakarta.persistence.EntityManager;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * Encola EnvioCorreo en Pendiente. No envía SMTP.
 * Misma claveIdempotencia + mismos datos inmutables = éxito en cualquier estado (D7).
 * No reactiva Fallido ni reencola Enviado.
 */
public class RegistroEnvioCorreoJpaAdapter implements RegistroEnvioCorreoPort {

    private final EntityManager em;

    public RegistroEnvioCorreoJpaAdapter(EntityManager em) {
        this.em = Objects.requireNonNull(em, "em");
    }

    @Override
    public void encolar(EnvioCorreoConvocatoria solicitud) {
        var evento = solicitud.evento();
        em.createNativeQuery(
                        """
                        INSERT INTO "EnvioCorreo" (
                          "idEnvioCorreo", "idUsuarioDestino", "idConvocatoria", "correoDestino",
                          "asunto", "contenido", "estadoEnvio", "numeroIntentos", "claveIdempotencia")
                        VALUES (
                          :id, :usuario, :convocatoria, :correo,
                          :asunto, :contenido, :estado, 0, :clave)
                        ON CONFLICT ("claveIdempotencia") DO NOTHING
                        """)
                .setParameter("id", UUID.randomUUID())
                .setParameter("usuario", evento.usuarioDestino())
                .setParameter("convocatoria", evento.convocatoriaId())
                .setParameter("correo", evento.correoDestino())
                .setParameter("asunto", solicitud.asunto())
                .setParameter("contenido", solicitud.contenido())
                .setParameter("estado", EstadoEnvioCorreo.PENDIENTE)
                .setParameter("clave", evento.claveIdempotencia())
                .executeUpdate();

        @SuppressWarnings("unchecked")
        List<Object[]> filas = em.createNativeQuery(
                        """
                        SELECT "idUsuarioDestino", "idConvocatoria", "correoDestino",
                               "asunto", "contenido"
                        FROM "EnvioCorreo"
                        WHERE "claveIdempotencia" = :clave
                        """)
                .setParameter("clave", evento.claveIdempotencia())
                .getResultList();

        if (filas.size() != 1) {
            throw new IllegalStateException("Encolado inconsistente: clave ausente o ambigua");
        }
        Object[] fila = filas.getFirst();
        UUID usuario = (UUID) fila[0];
        UUID convocatoria = (UUID) fila[1];
        String correo = (String) fila[2];
        String asunto = (String) fila[3];
        String contenido = (String) fila[4];
        if (!evento.usuarioDestino().equals(usuario)
                || !evento.convocatoriaId().equals(convocatoria)
                || !evento.correoDestino().equals(correo)
                || !solicitud.asunto().equals(asunto)
                || !solicitud.contenido().equals(contenido)) {
            throw new IllegalStateException(
                    "Encolado inconsistente: clave reutilizada con datos inmutables incompatibles");
        }
    }
}
