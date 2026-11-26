package com.prisaly.m5.application.port;

import com.prisaly.m5.domain.investigacion.Edificio;
import com.prisaly.m5.domain.investigacion.Nota;
import com.prisaly.m5.domain.investigacion.Pista;
import com.prisaly.m5.domain.investigacion.Zona;
import java.util.List;
import java.util.UUID;

/** Puertos de investigación: lectura y inserción por idempotencia (UUID del dominio). */
public interface PortInvestigacion {
    List<Nota> notasDeIntento(UUID idIntento);
    List<Pista> pistasDeIntento(UUID idIntento);
    List<Edificio> edificiosDeIntento(UUID idIntento);
    List<Zona> zonasDeIntento(UUID idIntento);

    void insertar(Nota nota);
    void insertar(Pista pista);
    void insertar(Edificio edificio);
    void insertar(Zona zona);
}
