package com.prisaly.m6.application.port;

import com.prisaly.m6.application.integration.ContextoAcademico;
import java.util.Optional;
import java.util.UUID;

/** M6-owned read contract, not a public producer API of M3. */
public interface ConsultaContextoAcademicoPort {
    /** Null UUID is invalid; empty denotes absence, not an empty or unauthorized case. */
    Optional<ContextoAcademico> findByCasoId(UUID idCaso);
}
