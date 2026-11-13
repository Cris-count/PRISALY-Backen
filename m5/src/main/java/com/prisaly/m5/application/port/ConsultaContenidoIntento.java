package com.prisaly.m5.application.port;

import com.prisaly.m5.application.ContenidoIntento;
import com.prisaly.m5.application.ReferenciaIntento;
import com.prisaly.m5.application.acceso.IdentificadorEstudiante;

/** Salida interna: identidad acreditada por el proxy M5, nunca por el cliente HTTP.
 * Se construye dentro del ensamblaje protegido, sin publicar un bean de consulta sin control.
 */
public interface ConsultaContenidoIntento {
    ContenidoIntento consultar(ReferenciaIntento intento, IdentificadorEstudiante estudiante);

    static ConsultaContenidoIntento noDisponible() {
        return (intento, estudiante) -> {
            throw new com.prisaly.m5.application.ContenidoIntentoNoDisponibleException();
        };
    }
}
