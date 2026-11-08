package com.prisaly.m6.application.service;

import com.prisaly.m6.application.AccesoEvaluacionDenegadoException;
import com.prisaly.m6.application.PreparacionContextoException;
import com.prisaly.m6.application.PreparacionContextoException.Codigo;
import com.prisaly.m6.application.integration.ContextoAcademico;
import com.prisaly.m6.application.integration.ContextoEvaluacionPreparado;
import com.prisaly.m6.application.integration.IntentoEvaluacion;
import com.prisaly.m6.application.port.ConsultaContextoAcademicoPort;
import com.prisaly.m6.application.port.ConsultaIntentoEvaluacionPort;
import com.prisaly.m6.application.port.SolicitudCapacidadContextoAcademicoPort;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/** Internal, explicitly wired preparation only. No Strategy, database writer or automatic bean. */
public final class PrepararContextoEvaluacionService {
    private final ConsultaIntentoEvaluacionPort intentos;
    private final SolicitudCapacidadContextoAcademicoPort capacidades;
    private final ConsultaContextoAcademicoPort contextos;

    public PrepararContextoEvaluacionService(ConsultaIntentoEvaluacionPort intentos,
            SolicitudCapacidadContextoAcademicoPort capacidades, ConsultaContextoAcademicoPort contextos) {
        this.intentos = Objects.requireNonNull(intentos, "intentos");
        this.capacidades = capacidades; // An absent institutional provider must fail closed, never use a default.
        this.contextos = Objects.requireNonNull(contextos, "contextos");
    }

    public ContextoEvaluacionPreparado preparar(UUID idIntento) {
        Objects.requireNonNull(idIntento, "idIntento");
        Optional<IntentoEvaluacion> found;
        try {
            found = Objects.requireNonNull(intentos.findById(idIntento));
        } catch (AccesoEvaluacionDenegadoException denied) {
            throw new AccesoEvaluacionDenegadoException();
        } catch (RuntimeException failure) {
            throw error(Codigo.FALLO_CONSULTA_INTENTO);
        }
        IntentoEvaluacion intento = found.orElseThrow(() -> error(Codigo.INTENTO_INEXISTENTE));
        if (!idIntento.equals(intento.idIntento())) { throw error(Codigo.UUID_INTENTO_INCONSISTENTE); }
        if (intento.estado() != IntentoEvaluacion.EstadoIntento.FINALIZADO) {
            throw error(Codigo.INTENTO_NO_FINALIZADO);
        }
        if (capacidades == null) { throw error(Codigo.EMISOR_AUSENTE); }
        String capacidad;
        try {
            capacidad = capacidades.solicitarCapacidad(intento.idIntento(), intento.idCaso());
        } catch (AccesoEvaluacionDenegadoException denied) {
            throw new AccesoEvaluacionDenegadoException();
        } catch (RuntimeException failure) {
            throw error(Codigo.EMISOR_NO_DISPONIBLE);
        }
        if (capacidad == null || capacidad.isBlank()) { throw error(Codigo.CAPACIDAD_INVALIDA); }
        Optional<ContextoAcademico> loaded;
        try {
            loaded = Objects.requireNonNull(contextos.findByCasoId(intento.idCaso(), capacidad));
        } catch (AccesoEvaluacionDenegadoException denied) {
            // Do not attach a capability-bearing provider cause to the outward error.
            throw new AccesoEvaluacionDenegadoException();
        } catch (RuntimeException failure) {
            throw error(Codigo.FALLO_CONSULTA_CONTEXTO);
        }
        ContextoAcademico contexto = loaded.orElseThrow(() -> error(Codigo.CONTEXTO_INEXISTENTE));
        if (!intento.idCaso().equals(contexto.idCaso())) { throw error(Codigo.CASO_INCONSISTENTE); }
        assertReferences(intento, contexto);
        return new ContextoEvaluacionPreparado(intento, contexto);
    }

    private static void assertReferences(IntentoEvaluacion intento, ContextoAcademico contexto) {
        Map<UUID, ContextoAcademico.Pregunta> preguntas = new HashMap<>();
        Set<UUID> escenarios = new HashSet<>();
        Set<UUID> opciones = new HashSet<>();
        for (var escenario : contexto.escenarios()) {
            require(contexto.idCaso().equals(escenario.idCaso()) && escenarios.add(escenario.idEscenario()));
            for (var pregunta : escenario.preguntas()) {
                require(escenario.idEscenario().equals(pregunta.idEscenario())
                        && preguntas.putIfAbsent(pregunta.idPregunta(), pregunta) == null);
                for (var opcion : pregunta.opciones()) {
                    require(pregunta.idPregunta().equals(opcion.idPregunta()) && opciones.add(opcion.idOpcion()));
                }
            }
        }
        // Rubric associations must belong to the rubric and reference this case's questions once each
        // (structure only, no weights policy). Rubric-case match stays in PrepararEvaluacionProtegida.
        var rubrica = contexto.rubrica();
        if (rubrica != null) {
            Set<UUID> asociaciones = new HashSet<>();
            Set<UUID> evaluadas = new HashSet<>();
            for (var asociacion : rubrica.preguntas()) {
                require(rubrica.idRubrica().equals(asociacion.idRubrica())
                        && asociaciones.add(asociacion.idRubricaPregunta())
                        && preguntas.containsKey(asociacion.idPregunta())
                        && evaluadas.add(asociacion.idPregunta()));
            }
        }
        Set<UUID> respondidas = new HashSet<>();
        for (var respuesta : intento.respuestas()) {
            var pregunta = preguntas.get(respuesta.idPregunta());
            require(pregunta != null && respondidas.add(respuesta.idPregunta()));
            require(pregunta.opciones().stream().anyMatch(o -> o.idOpcion().equals(respuesta.idOpcionSeleccionada())));
        }
    }

    private static void require(boolean valid) {
        if (!valid) { throw error(Codigo.REFERENCIAS_INCONSISTENTES); }
    }

    private static PreparacionContextoException error(Codigo codigo) { return new PreparacionContextoException(codigo); }
}
