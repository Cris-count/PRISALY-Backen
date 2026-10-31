package com.prisaly.m3.domain.builder;

import com.prisaly.m3.domain.model.Caso;
import com.prisaly.m3.domain.model.CasoEstado;
import com.prisaly.m3.domain.model.CriterioRubrica;
import com.prisaly.m3.domain.model.Escenario;
import com.prisaly.m3.domain.model.OpcionRespuesta;
import com.prisaly.m3.domain.model.Pregunta;
import com.prisaly.m3.domain.model.Rubrica;
import com.prisaly.m3.domain.rule.CasoDomainException;
import com.prisaly.m3.domain.rule.CasoValidation;
import java.util.ArrayList;
import java.util.List;
import java.time.Instant;

/**
 * Builds a complete academic case structure. Incomplete drafts are allowed;
 * invalid single-choice questions are rejected immediately with an identifiable error.
 */
public final class CasoBuilder {

    public static final String CODE_BUILDER_INCOMPLETE = "BUILDER_INCOMPLETE";

    private String id;
    private String titulo;
    private String docenteId;
    private Integer tiempoEstimadoSegundos;
    private String historia;
    private String desarrolloSituacional;
    private boolean activo = true; // V001 default; no activation operation is introduced.
    private Instant fechaCreacion;
    private Instant fechaActualizacion;
    private final List<EscenarioDraft> escenarios;
    private String rubricaId;
    private final List<CriterioRubrica> criterios;

    public CasoBuilder() {
        this.escenarios = new ArrayList<>();
        this.criterios = new ArrayList<>();
    }

    public CasoBuilder withId(String caseId) {
        this.id = caseId;
        return this;
    }

    public CasoBuilder withTitulo(String caseTitle) {
        this.titulo = caseTitle;
        return this;
    }

    public CasoBuilder withDocenteId(String ownerDocenteId) {
        this.docenteId = ownerDocenteId;
        return this;
    }

    public CasoBuilder withTiempoEstimadoSegundos(int tiempoEstimadoSegundos) {
        this.tiempoEstimadoSegundos = tiempoEstimadoSegundos;
        return this;
    }

    public CasoBuilder withHistoria(String historia) {
        this.historia = historia;
        return this;
    }

    public CasoBuilder withDesarrolloSituacional(String desarrolloSituacional) {
        this.desarrolloSituacional = desarrolloSituacional;
        return this;
    }

    public CasoBuilder withActivo(boolean activo) {
        this.activo = activo;
        return this;
    }

    /** Carries a supplied instant; never generates a creation date. */
    public CasoBuilder withFechaCreacion(Instant fechaCreacion) {
        this.fechaCreacion = fechaCreacion;
        return this;
    }

    public CasoBuilder withFechaActualizacion(Instant fechaActualizacion) {
        this.fechaActualizacion = fechaActualizacion;
        return this;
    }

    public CasoBuilder withRubricaId(String rubricId) {
        this.rubricaId = rubricId;
        return this;
    }

    public CasoBuilder addCriterio(String criterioId, String descripcion, int puntaje) {
        this.criterios.add(new CriterioRubrica(criterioId, descripcion, puntaje));
        return this;
    }

    public EscenarioStep addEscenario(String escenarioId, String descripcion) {
        EscenarioDraft draft = new EscenarioDraft(escenarioId, descripcion);
        this.escenarios.add(draft);
        return new EscenarioStep(this, draft);
    }

    /** Explicit physical content; keeps supplied orders, including gaps. */
    public EscenarioStep addEscenario(String escenarioId, String titulo, String contexto,
            String instrucciones, int orden, boolean activo) {
        EscenarioDraft draft = new EscenarioDraft(escenarioId, titulo, contexto, instrucciones, orden, activo);
        this.escenarios.add(draft);
        return new EscenarioStep(this, draft);
    }

    public Caso buildDraft() {
        if (id == null || id.isBlank()) {
            throw new CasoDomainException(CODE_BUILDER_INCOMPLETE, "Case id is required");
        }
        if (titulo == null || titulo.isBlank()) {
            throw new CasoDomainException(CODE_BUILDER_INCOMPLETE, "Case title is required");
        }
        if (docenteId == null || docenteId.isBlank()) {
            throw new CasoDomainException(CODE_BUILDER_INCOMPLETE, "Case teacher id is required");
        }

        if (tiempoEstimadoSegundos == null) {
            throw new CasoDomainException(CODE_BUILDER_INCOMPLETE, "Case estimated time is required");
        }
        CasoValidation.assertValidEstimatedTime(tiempoEstimadoSegundos);

        List<Escenario> builtEscenarios = new ArrayList<>();
        for (EscenarioDraft draft : escenarios) {
            builtEscenarios.add(draft.toEscenario());
        }

        Rubrica rubrica = null;
        if (rubricaId != null && !rubricaId.isBlank()) {
            rubrica = new Rubrica(rubricaId, List.copyOf(criterios));
        }

        return new Caso(
                id,
                titulo,
                docenteId,
                tiempoEstimadoSegundos,
                CasoEstado.BORRADOR,
                List.copyOf(builtEscenarios),
                rubrica,
                false,
                historia,
                desarrolloSituacional,
                activo,
                fechaCreacion,
                fechaActualizacion,
                null);
    }

    /**
     * Fluent step that adds validated single-choice questions to a scenario.
     */
    public static final class EscenarioStep {

        private final CasoBuilder builder;
        private final EscenarioDraft draft;

        private EscenarioStep(CasoBuilder builder, EscenarioDraft draft) {
            this.builder = builder;
            this.draft = draft;
        }

        public EscenarioStep addPreguntaSeleccionUnica(
                String preguntaId,
                String enunciado,
                List<OpcionRespuesta> opciones) {
            return addPreguntaSeleccionUnica(new Pregunta(preguntaId, enunciado, opciones));
        }

        /** Accepts all explicitly supplied question metadata without changing its editing state. */
        public EscenarioStep addPreguntaSeleccionUnica(Pregunta pregunta) {
            CasoValidation.assertValidSingleChoiceQuestion(pregunta);
            draft.preguntas.add(pregunta);
            return this;
        }

        public CasoBuilder and() {
            return builder;
        }
    }

    private static final class EscenarioDraft {

        private final String id;
        private final String descripcion;
        private final String titulo;
        private final String contexto;
        private final String instrucciones;
        private final Integer orden;
        private final boolean activo;
        private final List<Pregunta> preguntas;

        private EscenarioDraft(String id, String descripcion) {
            this.id = id;
            this.descripcion = descripcion;
            this.titulo = null;
            this.contexto = null;
            this.instrucciones = null;
            this.orden = null;
            this.activo = true;
            this.preguntas = new ArrayList<>();
        }

        private EscenarioDraft(String id, String titulo, String contexto, String instrucciones,
                int orden, boolean activo) {
            this.id = id;
            this.descripcion = null;
            this.titulo = titulo;
            this.contexto = contexto;
            this.instrucciones = instrucciones;
            this.orden = orden;
            this.activo = activo;
            this.preguntas = new ArrayList<>();
        }

        private Escenario toEscenario() {
            if (orden == null) {
                return new Escenario(id, descripcion, List.copyOf(preguntas));
            }
            return new Escenario(id, titulo, contexto, instrucciones, orden, activo, List.copyOf(preguntas));
        }
    }
}
