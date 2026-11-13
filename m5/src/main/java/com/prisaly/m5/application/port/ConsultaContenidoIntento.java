package com.prisaly.m5.application.port;

import com.prisaly.m3.api.ContenidoCasoEstudiantilPort.Caso;
import com.prisaly.m5.application.ReferenciaIntento;
import java.util.UUID;

/** Lectura para el estudiante autenticado por M1, nunca para una identidad del cliente. */
public interface ConsultaContenidoIntento {
    Contenido consultar(ReferenciaIntento intento);
    record Contenido(UUID idIntento, UUID idSesion, Caso caso) { }
}
