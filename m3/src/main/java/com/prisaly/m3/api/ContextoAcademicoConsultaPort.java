package com.prisaly.m3.api;

import java.util.Optional;
import java.util.UUID;

/** Protected query. An opaque capability is untrusted until the required verifier accepts it. */
public interface ContextoAcademicoConsultaPort {
    /** Empty means absence only after successful institutional verification. Never log capacidad. */
    Optional<ContextoAcademicoPersistido> consultar(UUID idCaso, String capacidad);
}
