package com.prisaly.m5.infrastructure.persistencia;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

/** Acceso Spring Data a {@code "Respuesta"}; uso exclusivo del adaptador de M5. */
public interface RespuestaJpaRepository extends JpaRepository<RespuestaEntity, UUID> {

    /**
     * Respuestas del intento ordenadas por pregunta. BD-005.2 no registra el
     * momento del primer registro, así que el orden original no se conserva.
     */
    List<RespuestaEntity> findByIdIntentoOrderByIdPreguntaAsc(UUID idIntento);
}
