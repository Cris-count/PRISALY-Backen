package com.prisaly.m5.application;

/**
 * Se intentó registrar o cambiar una respuesta después de {@code "Intento"."limiteTemporal"}.
 *
 * <p>DC-CLAUDE-BE-EJECUCION-04, A1. El instante de decisión es el reloj de PostgreSQL
 * ({@code clock_timestamp()}) leído con la fila del intento bloqueada, el mismo que se
 * grabaría como {@code "Respuesta"."fechaActualizacion"}. La operación no escribe nada y las
 * respuestas ya persistidas se conservan. No finaliza el intento ni decide qué ocurre con un
 * intento incompleto: eso sigue siendo una decisión académica pendiente.
 */
public final class LimiteTemporalVencidoException extends ParticipacionAplicacionException {

    private static final long serialVersionUID = 1L;

    public LimiteTemporalVencidoException() {
        super("El límite temporal del intento venció: no se admiten cambios en las respuestas");
    }
}
