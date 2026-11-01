package com.prisaly.m6.application.port;

import com.prisaly.m6.domain.model.RetroalimentacionGeneral;
import java.util.Optional;
import java.util.UUID;

/** Internal read port: null identifiers are invalid; empty means absence, never a persistence failure. */
public interface ConsultaRetroalimentacionPort {
    Optional<RetroalimentacionGeneral> findById(UUID idRetroalimentacion);
    Optional<RetroalimentacionGeneral> findByParticipacionId(UUID idParticipacion);
}
