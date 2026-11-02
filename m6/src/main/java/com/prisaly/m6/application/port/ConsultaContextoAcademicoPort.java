package com.prisaly.m6.application.port;

import com.prisaly.m6.application.integration.ContextoAcademico;
import java.util.Optional;
import java.util.UUID;

/** M6-owned read contract, not a public producer API of M3. */
public interface ConsultaContextoAcademicoPort {
    /**
     * UUID and capability are required. The app adapter must invoke M3's protected producer.
     * Empty denotes absence only after institutional verification, never denied access or failure.
     * The capability must not be logged or included in errors. Denial uses M6's
     * AccesoEvaluacionDenegadoException; technical failures must propagate distinctly.
     */
    Optional<ContextoAcademico> findByCasoId(UUID idCaso, String capacidad);
}
