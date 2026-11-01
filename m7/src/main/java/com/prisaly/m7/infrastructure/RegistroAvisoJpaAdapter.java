package com.prisaly.m7.infrastructure;

import com.prisaly.m7.application.RegistroAvisoPort;
import com.prisaly.m7.domain.AvisoConvocatoria;
import jakarta.persistence.EntityManager;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * Upsert atómico. Misma clave + mismo contenido = éxito sin duplicar.
 * Misma clave + datos incompatibles = rechazo explícito.
 */
public class RegistroAvisoJpaAdapter implements RegistroAvisoPort {

    private final EntityManager em;

    public RegistroAvisoJpaAdapter(EntityManager em) {
        this.em = Objects.requireNonNull(em, "em");
    }

    @Override
    public void registrar(AvisoConvocatoria aviso) {
        var evento = aviso.evento();
        em.createNativeQuery(
                        """
                        INSERT INTO "Aviso" ("idAviso", "idUsuarioDestino", "tipo", "contenido", "claveEvento")
                        VALUES (:id, :usuario, :tipo, :contenido, :clave)
                        ON CONFLICT ("idUsuarioDestino", "claveEvento") DO NOTHING
                        """)
                .setParameter("id", UUID.randomUUID())
                .setParameter("usuario", evento.usuarioDestino())
                .setParameter("tipo", AvisoConvocatoria.TIPO)
                .setParameter("contenido", aviso.contenido())
                .setParameter("clave", evento.claveEvento())
                .executeUpdate();

        @SuppressWarnings("unchecked")
        List<Object[]> filas = em.createNativeQuery(
                        """
                        SELECT "tipo", "contenido", "idParticipacion"
                        FROM "Aviso"
                        WHERE "idUsuarioDestino" = :usuario AND "claveEvento" = :clave
                        """)
                .setParameter("usuario", evento.usuarioDestino())
                .setParameter("clave", evento.claveEvento())
                .getResultList();

        if (filas.size() != 1) {
            throw new IllegalStateException("Registro de aviso inconsistente: clave ausente o ambigua");
        }
        Object[] fila = filas.getFirst();
        String tipo = (String) fila[0];
        String contenido = (String) fila[1];
        Object participacion = fila[2];
        if (!AvisoConvocatoria.TIPO.equals(tipo)
                || participacion != null
                || !aviso.contenido().equals(contenido)) {
            throw new IllegalStateException(
                    "Registro de aviso inconsistente: clave reutilizada con datos incompatibles");
        }
    }
}
