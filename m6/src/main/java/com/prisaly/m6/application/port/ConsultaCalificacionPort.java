package com.prisaly.m6.application.port;

import com.prisaly.m6.domain.model.Calificacion;
import java.util.Optional;
import java.util.UUID;

/** Internal read port. Null identifiers are invalid; empty means no grade, never a database failure. */
public interface ConsultaCalificacionPort {
    Optional<Calificacion> findById(UUID idCalificacion);
    Optional<Calificacion> findByIntentoId(UUID idIntento);
}
