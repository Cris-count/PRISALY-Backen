package com.prisaly.m7.application;

import com.prisaly.m7.domain.ConvocatoriaNotificable;
import java.util.List;
import java.util.Objects;

/** Observer explícito: registro requerido primero, luego extensiones síncronas. */
public final class PublicadorConvocatoria {
    private final ObservadorRegistroAviso registro;
    private final List<ObservadorConvocatoria> adicionales;

    public PublicadorConvocatoria(ObservadorRegistroAviso registro,
            List<ObservadorConvocatoria> adicionales) {
        if (registro == null) throw new IllegalStateException("Falta el observador de registro requerido");
        this.registro = registro;
        this.adicionales = List.copyOf(adicionales);
    }

    public void publicar(ConvocatoriaNotificable evento) {
        Objects.requireNonNull(evento);
        registro.actualizar(evento);
        adicionales.forEach(observador -> observador.actualizar(evento));
    }
}
