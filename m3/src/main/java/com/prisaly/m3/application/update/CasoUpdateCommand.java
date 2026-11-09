package com.prisaly.m3.application.update;

import com.prisaly.m3.domain.model.CriterioRubrica;
import com.prisaly.m3.domain.rule.RubricaStructureValidation;
import java.math.BigDecimal;
import java.util.List;
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

    /** Explicit correctness; no defaults, implicit deletion or renumbering. */
    record AgregarOpcionRespuestaCommand(UUID idOpcion, UUID idPregunta, String texto,
            boolean esCorrecta, String explicacion, int orden) implements CasoUpdateCommand {
        public AgregarOpcionRespuestaCommand {
            Objects.requireNonNull(idOpcion, "idOpcion");
            Objects.requireNonNull(idPregunta, "idPregunta");
            requiredText(texto, Integer.MAX_VALUE);
            positiveInteger(orden);
        }
    }

    /** Explicit initial editing state/visibility; dates use the official SQL creation default. */
    record AgregarPreguntaCommand(UUID idPregunta, UUID idEscenario, String enunciado,
            String textoAyuda, String estadoEdicion, boolean visible, int orden) implements CasoUpdateCommand {
        public AgregarPreguntaCommand {
            Objects.requireNonNull(idPregunta, "idPregunta");
            Objects.requireNonNull(idEscenario, "idEscenario");
            requiredText(enunciado, Integer.MAX_VALUE);
            if (!"Borrador".equals(estadoEdicion) && !"Lista".equals(estadoEdicion)) {
                throw new IllegalArgumentException("estadoEdicion must be Borrador or Lista");
            }
            positiveInteger(orden);
        }
    }

    /** Empty scenario; initial activity is the official SQL DEFAULT true. */
    record AgregarEscenarioCommand(UUID idEscenario, String titulo, String contexto,
            String instrucciones, int orden) implements CasoUpdateCommand {
        public AgregarEscenarioCommand {
            Objects.requireNonNull(idEscenario, "idEscenario");
            optionalText(titulo, 200);
            positiveInteger(orden);
        }
    }

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

    record AsociacionRubrica(UUID idPregunta, BigDecimal valorPregunta, String criterio) {
        public AsociacionRubrica {
            Objects.requireNonNull(idPregunta, "idPregunta");
            RubricaStructureValidation.requirePositiveNumeric8_2(valorPregunta);
        }
    }

    /** Creates or replaces the single rubric of the case. Empty criteria keep it incomplete. */
    record GuardarRubricaCommand(String nombre, String descripcion, BigDecimal puntajeTotal,
            List<CriterioRubrica> criterios, List<AsociacionRubrica> preguntas) implements CasoUpdateCommand {
        public GuardarRubricaCommand {
            requiredText(nombre, 150);
            optionalText(descripcion, Integer.MAX_VALUE);
            if (puntajeTotal != null) { RubricaStructureValidation.requirePositiveNumeric8_2(puntajeTotal); }
            criterios = List.copyOf(criterios);
            preguntas = List.copyOf(preguntas);
            if (criterios.stream().map(CriterioRubrica::id).distinct().count() != criterios.size()) {
                throw new IllegalArgumentException("Criteria identifiers must be unique");
            }
            if (preguntas.stream().map(AsociacionRubrica::idPregunta).distinct().count() != preguntas.size()) {
                throw new IllegalArgumentException("A question can be associated only once");
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
