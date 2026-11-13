package com.prisaly.m5.application;

import com.prisaly.m3.api.ContenidoCasoEstudiantilPort.Caso;
import java.util.UUID;

/** Contenido autorizado del intento; conserva el contrato JSON del simulador. */
public record ContenidoIntento(UUID idIntento, UUID idSesion, Caso caso) { }
