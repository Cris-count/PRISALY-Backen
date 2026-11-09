package com.prisaly.m7.application;

import com.prisaly.m7.domain.ConvocatoriaNotificable;
import com.prisaly.m7.domain.EnvioCorreoConvocatoria;
import java.util.Objects;

/** Encola el correo de la convocatoria en la misma transacción del aviso. */
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
