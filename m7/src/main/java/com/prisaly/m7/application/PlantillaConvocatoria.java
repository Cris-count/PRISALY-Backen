package com.prisaly.m7.application;

import com.prisaly.m7.domain.ConvocatoriaNotificable;

/** Sustituye los datos del evento en los textos configurados del aviso y del correo. */
final class PlantillaConvocatoria {
    private PlantillaConvocatoria() {
    }

    static String componer(String plantilla, ConvocatoriaNotificable evento) {
        return plantilla
                .replace("{sesionId}", evento.sesionId().toString())
                .replace("{nombreCaso}", texto(evento.nombreCaso()))
                .replace("{nombreSesion}", texto(evento.nombreSesion()))
                .replace("{inicioPrevisto}", texto(evento.inicioPrevisto()))
                .replace("{finPrevisto}", texto(evento.finPrevisto()))
                .replace("{mensajePersonalizado}", texto(evento.mensajePersonalizado()))
                .strip();
    }

    private static String texto(String valor) {
        return valor == null ? "" : valor;
    }
}
