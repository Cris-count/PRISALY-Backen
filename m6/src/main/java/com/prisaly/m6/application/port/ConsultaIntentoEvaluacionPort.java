package com.prisaly.m6.application.port;

import com.prisaly.m6.application.integration.IntentoEvaluacion;
import java.util.Optional;
import java.util.UUID;

/** Consumer contract only. No adapter or certification of academic eligibility. */
public interface ConsultaIntentoEvaluacionPort {
    /** Null UUID is invalid; empty denotes absence, technical failures must propagate. */
    Optional<IntentoEvaluacion> findById(UUID idIntento);
}
