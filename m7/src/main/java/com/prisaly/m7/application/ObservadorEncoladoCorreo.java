package com.prisaly.m7.application;

import com.prisaly.m7.domain.ConvocatoriaNotificable;
import com.prisaly.m7.domain.EnvioCorreoConvocatoria;
import java.util.Objects;

/**
 * Observador adicional. En la misma transacción deja una fila de correo pendiente.
 * No lo envía: solo encola asunto y cuerpo ya compuestos, recortando el asunto a 255.
 */
public final class ObservadorEncoladoCorreo implements ObservadorConvocatoria {
    private static final int LIMITE_ASUNTO = 255;

    private final RegistroEnvioCorreoPort registro;
    private final String plantillaAsunto;
    private final String plantillaContenido;

    public ObservadorEncoladoCorreo(RegistroEnvioCorreoPort registro, String plantillaAsunto, String plantillaContenido) {
        this.registro = Objects.requireNonNull(registro, "registroEnvio");
        this.plantillaAsunto = plantillaAsunto;
        this.plantillaContenido = plantillaContenido;
    }

    @Override
    public void actualizar(ConvocatoriaNotificable evento) {
        String asunto = PlantillaConvocatoria.componer(plantillaAsunto, evento);
        registro.encolar(new EnvioCorreoConvocatoria(evento,
                asunto.length() <= LIMITE_ASUNTO ? asunto : asunto.substring(0, LIMITE_ASUNTO),
                PlantillaConvocatoria.componer(plantillaContenido, evento)));
    }
}
