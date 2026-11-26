package com.prisaly.m5.application.acceso;

import com.prisaly.m5.domain.investigacion.Edificio;
import com.prisaly.m5.domain.investigacion.Nota;
import com.prisaly.m5.domain.investigacion.Pista;
import com.prisaly.m5.domain.investigacion.Zona;
import java.util.List;
import java.util.UUID;

/**
 * Investigación del intento expuesta a presentación SOLO tras el control de acceso (DC-111-CIERRE). Cada operación
 * resuelve primero el intento mediante {@link AccesoProtegidoParticipacion}, que exige la identidad estudiantil real y
 * la titularidad; presentación depende de este contrato y nunca del servicio de aplicación ni de infraestructura
 * (ArquitecturaAccesoProtegidoTest, ArquitecturaCapasTest).
 */
public interface InvestigacionProtegida {

    List<Nota> notas(UUID idIntento);

    List<Pista> pistas(UUID idIntento);

    List<Edificio> edificios(UUID idIntento);

    List<Zona> zonas(UUID idIntento);

    Nota guardarNota(UUID idIntento, String titulo, String texto);

    Pista guardarPista(UUID idIntento, String descripcion);

    Edificio guardarEdificio(UUID idIntento, String nombre, String ubicacion);

    Zona guardarZona(UUID idIntento, String nombre, String descripcion);
}
