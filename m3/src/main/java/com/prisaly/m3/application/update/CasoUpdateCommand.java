package com.prisaly.m3.application.update;

import java.util.Objects;
import java.util.UUID;

/**
 * Explicit single-field operations. Omission means no command, not a null value.
 * Null is an assignment and is accepted only for documented nullable fields.
 * Ownership, activity, editing/lifecycle states and audit dates are not commands.
 */
public sealed interface CasoUpdateCommand {
    enum CampoCaso { NOMBRE, HISTORIA, DESARROLLO_SITUACIONAL, TIEMPO_ESTIMADO_SEGUNDOS }
    enum CampoEscenario { TITULO, CONTEXTO, INSTRUCCIONES, ORDEN }
    enum CampoPregunta { ENUNCIADO, TEXTO_AYUDA, ORDEN }

    record ActualizarDatosCasoCommand(CampoCaso campo, Object valor) implements CasoUpdateCommand {
        public ActualizarDatosCasoCommand {
            Objects.requireNonNull(campo, "campo");
            switch (campo) {
                case NOMBRE -> requiredText(valor, 150);
                case HISTORIA, DESARROLLO_SITUACIONAL -> optionalText(valor, Integer.MAX_VALUE);
                case TIEMPO_ESTIMADO_SEGUNDOS -> positiveInteger(valor);
            }
        }
    }

    record ActualizarEscenarioCommand(UUID idEscenario, CampoEscenario campo, Object valor)
            implements CasoUpdateCommand {
        public ActualizarEscenarioCommand {
            Objects.requireNonNull(idEscenario, "idEscenario");
            Objects.requireNonNull(campo, "campo");
            switch (campo) {
                case TITULO -> optionalText(valor, 200);
                case CONTEXTO, INSTRUCCIONES -> optionalText(valor, Integer.MAX_VALUE);
                case ORDEN -> positiveInteger(valor);
            }
        }
    }

    record ActualizarPreguntaCommand(UUID idPregunta, CampoPregunta campo, Object valor)
            implements CasoUpdateCommand {
        public ActualizarPreguntaCommand {
            Objects.requireNonNull(idPregunta, "idPregunta");
            Objects.requireNonNull(campo, "campo");
            switch (campo) {
                case ENUNCIADO -> requiredText(valor, Integer.MAX_VALUE);
                case TEXTO_AYUDA -> optionalText(valor, Integer.MAX_VALUE);
                case ORDEN -> positiveInteger(valor);
            }
        }
    }

    record EliminarOpcionCommand(UUID idOpcion) implements CasoUpdateCommand {
        public EliminarOpcionCommand { Objects.requireNonNull(idOpcion, "idOpcion"); }
    }

    private static void optionalText(Object value, int maximumLength) {
        if (value != null && (!(value instanceof String text)
                || text.codePointCount(0, text.length()) > maximumLength)) {
            throw new IllegalArgumentException("Expected nullable text within the field length");
        }
    }

    private static void requiredText(Object value, int maximumLength) {
        optionalText(value, maximumLength);
        if (!(value instanceof String text) || text.isBlank()) {
            throw new IllegalArgumentException("Nonblank text is required");
        }
    }

    private static void positiveInteger(Object value) {
        if (!(value instanceof Integer number) || number <= 0) {
            throw new IllegalArgumentException("A positive Integer is required without coercion");
        }
    }
}
