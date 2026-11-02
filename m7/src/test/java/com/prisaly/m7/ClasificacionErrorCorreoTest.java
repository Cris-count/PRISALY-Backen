package com.prisaly.m7;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.prisaly.m7.application.ClasificacionErrorCorreo;
import com.prisaly.m7.domain.ClaseErrorCorreo;
import org.junit.jupiter.api.Test;

class ClasificacionErrorCorreoTest {

    @Test
    void autenticacionEsConfiguracion() {
        assertEquals(
                ClaseErrorCorreo.CONFIGURACION,
                ClasificacionErrorCorreo.clasificar("535", "Authentication failed"));
    }

    @Test
    void destinatarioDesconocidoEsPermanente() {
        assertEquals(
                ClaseErrorCorreo.PERMANENTE_DESTINATARIO,
                ClasificacionErrorCorreo.clasificar("550", "User unknown"));
    }

    @Test
    void timeoutEsTransitorio() {
        assertEquals(ClaseErrorCorreo.TRANSITORIO, ClasificacionErrorCorreo.clasificar("421", "timeout"));
    }

    @Test
    void cincoXxGenericoEsConfiguracion() {
        assertEquals(ClaseErrorCorreo.CONFIGURACION, ClasificacionErrorCorreo.clasificar("554", "relay denied"));
    }
}
