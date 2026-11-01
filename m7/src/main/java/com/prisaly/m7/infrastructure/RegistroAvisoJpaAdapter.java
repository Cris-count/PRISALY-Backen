package com.prisaly.m7.infrastructure;

import com.prisaly.m7.application.RegistroAvisoPort;
import com.prisaly.m7.domain.AvisoConvocatoria;
import jakarta.persistence.EntityManager;
import java.util.UUID;

/** Adapter PostgreSQL: upsert atómico sin modificar lectura ni contenido de un aviso previo. */
public class RegistroAvisoJpaAdapter implements RegistroAvisoPort {
    private final EntityManager em;

    public RegistroAvisoJpaAdapter(EntityManager em) { this.em = em; }

    @Override
    public void registrar(AvisoConvocatoria aviso) {
        var evento = aviso.evento();
        em.createNativeQuery("""
                INSERT INTO "Aviso" ("idAviso", "idUsuarioDestino", "tipo", "contenido", "claveEvento")
                VALUES (:id, :usuario, :tipo, :contenido, :clave)
                ON CONFLICT ("idUsuarioDestino", "claveEvento") DO NOTHING
                """).setParameter("id", UUID.randomUUID())
                .setParameter("usuario", evento.usuarioDestino())
                .setParameter("tipo", AvisoConvocatoria.TIPO)
                .setParameter("contenido", aviso.contenido())
                .setParameter("clave", evento.claveEvento()).executeUpdate();
        // Una clave ocupada por otro tipo/participación no equivale a éxito.
        var tipos = em.createNativeQuery("""
                SELECT "tipo" FROM "Aviso"
                WHERE "idUsuarioDestino" = :usuario AND "claveEvento" = :clave
                  AND "idParticipacion" IS NULL AND length(trim("contenido")) > 0
                """).setParameter("usuario", evento.usuarioDestino())
                .setParameter("clave", evento.claveEvento()).getResultList();
        if (tipos.size() != 1 || !AvisoConvocatoria.TIPO.equals(tipos.getFirst())) {
            throw new IllegalStateException("Registro de aviso inconsistente");
        }
    }
}
