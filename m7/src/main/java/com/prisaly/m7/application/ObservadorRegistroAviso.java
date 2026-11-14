package com.prisaly.m7.application;

import com.prisaly.m7.domain.AvisoConvocatoria;
import com.prisaly.m7.domain.ConvocatoriaNotificable;
import java.util.Objects;

/**
 * Observador obligatorio de una convocatoria. Compone el texto con
 * {@link PlantillaConvocatoria} y lo guarda por {@link RegistroAvisoPort}.
 * No abre SMTP; el envío lo hace después {@link ProcesarEnviosPendientesService}.
 */
public final class ObservadorRegistroAviso implements ObservadorConvocatoria {
    private final RegistroAvisoPort registro;
    private final String texto;

    public ObservadorRegistroAviso(RegistroAvisoPort registro, String texto) {
        this.registro = Objects.requireNonNull(registro);
        if (texto == null || texto.isBlank()) throw new IllegalArgumentException("Contenido vacío");
        this.texto = texto;
    }

    @Override
    public void actualizar(ConvocatoriaNotificable evento) {
        registro.registrar(new AvisoConvocatoria(evento, PlantillaConvocatoria.componer(texto, evento)));
    }
}
