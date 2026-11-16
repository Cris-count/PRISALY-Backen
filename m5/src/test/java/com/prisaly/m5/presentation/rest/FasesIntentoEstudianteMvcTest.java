package com.prisaly.m5.presentation.rest;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.prisaly.m3.api.ContenidoCasoEstudiantilPort.Caso;
import com.prisaly.m3.api.ContenidoCasoEstudiantilPort.Escenario;
import com.prisaly.m3.api.ContenidoCasoEstudiantilPort.Pregunta;
import com.prisaly.m5.application.ContenidoIntento;
import com.prisaly.m5.application.IntentoNoEncontradoException;
import com.prisaly.m5.application.LimiteTemporalVencidoException;
import com.prisaly.m5.application.OperacionesParticipacion;
import com.prisaly.m5.application.ReferenciaConvocatoria;
import com.prisaly.m5.application.ReferenciaIntento;
import com.prisaly.m5.application.ResultadoInicio;
import com.prisaly.m5.application.ResumenIntento;
import com.prisaly.m5.application.acceso.AccesoProtegidoParticipacion;
import com.prisaly.m5.application.acceso.ControlAccesoIntentos;
import com.prisaly.m5.application.acceso.IdentidadEstudianteActual;
import com.prisaly.m5.application.acceso.IdentificadorEstudiante;
import com.prisaly.m5.application.acceso.Titularidad;
import com.prisaly.m5.application.fase.EntradaCuestionario;
import com.prisaly.m5.application.fase.EstadoFase;
import com.prisaly.m5.application.fase.ExploracionCerradaException;
import com.prisaly.m5.application.fase.FaseAcademica;
import com.prisaly.m5.application.fase.NarrativaIntento;
import com.prisaly.m5.application.fase.NarrativaNoDisponibleException;
import com.prisaly.m5.application.fase.CuestionarioNoIniciadoException;
import com.prisaly.m5.application.fase.SinFasesException;
import com.prisaly.m5.domain.intento.EstadoIntento;
import com.prisaly.m5.domain.intento.IntentoFinalizadoException;
import com.prisaly.m5.domain.intento.MotivoFinalizacion;
import com.prisaly.m5.domain.respuesta.ReferenciaPregunta;
import com.prisaly.m5.domain.respuesta.Respuesta;
import com.prisaly.m5.domain.respuesta.ValorRespuesta;
import com.prisaly.m5.presentation.ManejadorErroresM5;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Supplier;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

/**
 * DC-BE-SIM-B2B-B3-32 (B3): despacho MVC real de P1–P4 con el Proxy real y {@link ManejadorErroresM5}. Contratos DTO,
 * {@code Cache-Control: no-store}, errores tipados, titularidad e interruptores. La cadena de seguridad (sesión M1 y
 * {@code Origin}) y PostgreSQL se prueban en {@code app} ({@code FasesIntentoEstudianteHttpIT}).
 */
@DisplayName("FasesIntentoEstudianteControlador: contratos P1–P4")
class FasesIntentoEstudianteMvcTest {

    static final UUID INTENTO = UUID.randomUUID();
    static final UUID GUION = UUID.randomUUID();
    static final UUID PREGUNTA = UUID.randomUUID();
    static final Instant T0 = Instant.parse("2026-11-16T15:00:00Z");
    static final String RUTA = "/v1/estudiantes/intentos/" + INTENTO;

    final List<String> llamadas = new ArrayList<>();
    RuntimeException fallo;
    EstadoFase fase = new EstadoFase(INTENTO, EstadoIntento.EN_CURSO, FaseAcademica.EXPLORACION,
            Optional.of(T0.minusSeconds(60)), Optional.empty(), T0.plusSeconds(1800), T0);
    boolean cuestionarioIniciado;

    /** Operaciones M5 en memoria detrás del Proxy real; {@code fallo} simula la excepción de la capa de aplicación. */
    final class Operaciones implements OperacionesParticipacion {
        private <T> T responder(String operacion, Supplier<T> valor) {
            llamadas.add(operacion);
            if (fallo != null) {
                throw fallo;
            }
            return valor.get();
        }

        @Override public ResultadoInicio iniciarOReanudar(ReferenciaConvocatoria c) { throw new UnsupportedOperationException(); }
        @Override public ResumenIntento consultarIntento(ReferenciaIntento i) {
            return responder("respuestas", () -> new ResumenIntento(i, EstadoIntento.EN_CURSO, Optional.empty(), List.of()));
        }
        @Override public ResumenIntento registrarRespuesta(ReferenciaIntento i, ReferenciaPregunta p, ValorRespuesta v) { throw new UnsupportedOperationException(); }
        @Override public ResumenIntento actualizarRespuesta(ReferenciaIntento i, ReferenciaPregunta p, ValorRespuesta v) { throw new UnsupportedOperationException(); }
        @Override public Respuesta guardarRespuesta(ReferenciaIntento i, ReferenciaPregunta p, ValorRespuesta v, int r) { throw new UnsupportedOperationException(); }
        @Override public ResumenIntento finalizarIntento(ReferenciaIntento i, MotivoFinalizacion m) { throw new UnsupportedOperationException(); }
        @Override public EstadoFase consultarFase(ReferenciaIntento i) { return responder("fase", () -> fase); }
        @Override public EntradaCuestionario iniciarCuestionario(ReferenciaIntento i) {
            return responder("cuestionario", () -> {
                boolean ya = cuestionarioIniciado;
                cuestionarioIniciado = true;
                return new EntradaCuestionario(INTENTO, T0.plusSeconds(5), ya);
            });
        }
        @Override public NarrativaIntento consultarNarrativa(ReferenciaIntento i) {
            return responder("narrativa", () -> new NarrativaIntento(INTENTO, GUION, 3, "f".repeat(64),
                    "{\"formato\":\"prisaly-guion-1\",\"inicio\":\"entrada\"}"));
        }
    }

    AccesoProtegidoParticipacion proxy(IdentidadEstudianteActual identidad, Titularidad titularidad, boolean candidatas) {
        var caso = new Caso(UUID.randomUUID(), "Caso", null, null, List.of(new Escenario(UUID.randomUUID(), "E", null, null, 1,
                List.of(new Pregunta(PREGUNTA, "Enunciado privado", 1, List.of())))));
        return new AccesoProtegidoParticipacion(new ControlAccesoIntentos(identidad, (i, e) -> {
            llamadas.add("titularidad");
            return titularidad;
        }), new Operaciones(), (i, e) -> {
            llamadas.add("contenido");
            if (fallo instanceof CuestionarioNoIniciadoException) {
                throw fallo; // D-SIM-2 se evalúa antes de leer el contenido (lo aplica el ensamblaje)
            }
            return new ContenidoIntento(INTENTO, UUID.randomUUID(), caso);
        }, Supplier::get, candidatas);
    }

    MockMvc mvc(AccesoProtegidoParticipacion proxy) {
        return MockMvcBuilders.standaloneSetup(new FasesIntentoEstudianteControlador(proxy, true))
                .setControllerAdvice(new ManejadorErroresM5()).build();
    }

    MockMvc mvc() {
        return mvc(proxy(() -> Optional.of(new IdentificadorEstudiante("E-ana")), Titularidad.PROPIO, true));
    }

    static ResultActions sinCache(ResultActions r) throws Exception {
        return r.andExpect(header().string("Cache-Control", "no-store"));
    }

    // --- Contratos de éxito ------------------------------------------------------------------------------------

    @Test @DisplayName("P2 GET /fase: fase persistida, fechas, límite y hora del servidor; null donde no hay fecha")
    void fase() throws Exception {
        sinCache(mvc().perform(get(RUTA + "/fase"))).andExpect(status().isOk())
                .andExpect(jsonPath("$.idIntento").value(INTENTO.toString()))
                .andExpect(jsonPath("$.estadoIntento").value("EnCurso"))
                .andExpect(jsonPath("$.fase").value("EXPLORACION"))
                .andExpect(jsonPath("$.fechaInicioExploracion").value("2026-11-16T14:59:00Z"))
                .andExpect(jsonPath("$.fechaInicioCuestionario").isEmpty())
                .andExpect(jsonPath("$.limiteTemporal").value("2026-11-16T15:30:00Z"))
                .andExpect(jsonPath("$.ahoraServidor").value("2026-11-16T15:00:00Z"));
        fase = new EstadoFase(INTENTO, EstadoIntento.FINALIZADO, FaseAcademica.SIN_FASES, Optional.empty(), Optional.empty(),
                T0, T0);
        mvc().perform(get(RUTA + "/fase")).andExpect(status().isOk())
                .andExpect(jsonPath("$.fase").value("SIN_FASES")).andExpect(jsonPath("$.estadoIntento").value("Finalizado"))
                .andExpect(jsonPath("$.fechaInicioExploracion").isEmpty());
        assertEquals(List.of("titularidad", "fase", "titularidad", "fase"), llamadas);
    }

    @Test @DisplayName("P1 GET /narrativa: versión fijada con su huella; la definición se entrega como documento JSON")
    void narrativa() throws Exception {
        sinCache(mvc().perform(get(RUTA + "/narrativa"))).andExpect(status().isOk())
                .andExpect(jsonPath("$.idIntento").value(INTENTO.toString()))
                .andExpect(jsonPath("$.idGuion").value(GUION.toString()))
                .andExpect(jsonPath("$.version").value(3))
                .andExpect(jsonPath("$.hashDefinicion").value("f".repeat(64)))
                .andExpect(jsonPath("$.definicion.formato").value("prisaly-guion-1"))
                .andExpect(jsonPath("$.definicion.inicio").value("entrada"));
    }

    @Test @DisplayName("P3 POST /cuestionario: 200 siempre; el reintento devuelve la misma fecha con yaIniciado=true")
    void cuestionarioIdempotente() throws Exception {
        MockMvc mvc = mvc();
        sinCache(mvc.perform(post(RUTA + "/cuestionario"))).andExpect(status().isOk())
                .andExpect(jsonPath("$.fase").value("CUESTIONARIO"))
                .andExpect(jsonPath("$.fechaInicioCuestionario").value("2026-11-16T15:00:05Z"))
                .andExpect(jsonPath("$.yaIniciado").value(false));
        mvc.perform(post(RUTA + "/cuestionario")).andExpect(status().isOk())
                .andExpect(jsonPath("$.fechaInicioCuestionario").value("2026-11-16T15:00:05Z"))
                .andExpect(jsonPath("$.yaIniciado").value(true));
    }

    @Test @DisplayName("P4 GET /completitud: recuento informativo con identificadores, sin enunciados ni aciertos")
    void completitud() throws Exception {
        MvcResult r = sinCache(mvc().perform(get(RUTA + "/completitud"))).andExpect(status().isOk())
                .andExpect(jsonPath("$.idIntento").value(INTENTO.toString()))
                .andExpect(jsonPath("$.preguntasPresentables").value(1))
                .andExpect(jsonPath("$.respondidas").value(0))
                .andExpect(jsonPath("$.sinResponder[0]").value(PREGUNTA.toString())).andReturn();
        String cuerpo = r.getResponse().getContentAsString();
        for (String prohibido : List.of("Enunciado privado", "esCorrecta", "nota", "calificacion", "aprobado")) {
            assertFalse(cuerpo.contains(prohibido), prohibido);
        }
        assertEquals(List.of("titularidad", "contenido", "respuestas"), llamadas);
    }

    // --- Errores tipados ---------------------------------------------------------------------------------------

    record Caso409(String ruta, boolean post, RuntimeException error, int estado, String codigo) { }

    @Test @DisplayName("errores de fase: 409 con código propio por decisión; 404 inexistente; cuerpo {code, message} sin detalles")
    void erroresTipados() throws Exception {
        List<Caso409> casos = List.of(
                new Caso409("/completitud", false, new CuestionarioNoIniciadoException(), 409, "CUESTIONARIO_NO_INICIADO"),
                new Caso409("/cuestionario", true, new LimiteTemporalVencidoException("vencido"), 409, "LIMITE_TEMPORAL_VENCIDO"),
                new Caso409("/narrativa", false, new ExploracionCerradaException(), 409, "EXPLORACION_CERRADA"),
                new Caso409("/cuestionario", true, new IntentoFinalizadoException("iniciar el cuestionario"), 409, "INTENTO_FINALIZADO"),
                new Caso409("/cuestionario", true, new SinFasesException(), 409, "INTENTO_SIN_FASES"),
                new Caso409("/narrativa", false, new NarrativaNoDisponibleException(), 409, "NARRATIVA_NO_DISPONIBLE"),
                new Caso409("/fase", false, new IntentoNoEncontradoException(new ReferenciaIntento(INTENTO.toString())), 404,
                        "INTENTO_NO_ENCONTRADO"));
        for (Caso409 c : casos) {
            fallo = c.error();
            var peticion = c.post() ? post(RUTA + c.ruta()) : get(RUTA + c.ruta());
            MvcResult r = mvc().perform(peticion).andExpect(status().is(c.estado()))
                    .andExpect(jsonPath("$.code").value(c.codigo())).andReturn();
            String cuerpo = r.getResponse().getContentAsString();
            assertFalse(cuerpo.contains("Exception"), cuerpo);
            assertFalse(cuerpo.contains("Enunciado privado"), cuerpo);
        }
    }

    @Test @DisplayName("titularidad: sin identidad 403 STUDENT_IDENTITY_UNAVAILABLE e intento ajeno 403 FORBIDDEN, sin delegar")
    void titularidad() throws Exception {
        MockMvc sinIdentidad = mvc(proxy(Optional::empty, Titularidad.PROPIO, true));
        MockMvc ajeno = mvc(proxy(() -> Optional.of(new IdentificadorEstudiante("E-luis")), Titularidad.AJENO, true));
        for (String ruta : List.of("/narrativa", "/fase", "/completitud")) {
            sinIdentidad.perform(get(RUTA + ruta)).andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.code").value("STUDENT_IDENTITY_UNAVAILABLE"));
            ajeno.perform(get(RUTA + ruta)).andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("FORBIDDEN"));
        }
        sinIdentidad.perform(post(RUTA + "/cuestionario")).andExpect(status().isForbidden());
        ajeno.perform(post(RUTA + "/cuestionario")).andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("FORBIDDEN"));
        assertTrue(llamadas.stream().allMatch("titularidad"::equals), llamadas.toString());
        mvc().perform(get("/v1/estudiantes/intentos/no-uuid/fase")).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    }

    // --- Interruptores -----------------------------------------------------------------------------------------

    @Test @DisplayName("aun con la ruta montada, un Proxy sin fases responde 409 FASES_NO_DISPONIBLES sin identidad ni servicio")
    void rutaMontadaConFasesApagadas() throws Exception {
        MockMvc mvc = mvc(proxy(() -> Optional.of(new IdentificadorEstudiante("E-ana")), Titularidad.PROPIO, false));
        for (String ruta : List.of("/narrativa", "/fase", "/completitud")) {
            mvc.perform(get(RUTA + ruta)).andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("FASES_NO_DISPONIBLES"));
        }
        mvc.perform(post(RUTA + "/cuestionario")).andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("FASES_NO_DISPONIBLES"));
        assertEquals(List.of(), llamadas);
    }

    @Test @DisplayName("exposición HTTP: apagada por defecto; encendida sin fases el arranque falla cerrado")
    void interruptorHttp() {
        var proxy = proxy(() -> Optional.of(new IdentificadorEstudiante("E-ana")), Titularidad.PROPIO, true);
        var contexto = new ApplicationContextRunner()
                .withBean(AccesoProtegidoParticipacion.class, () -> proxy)
                .withUserConfiguration(FasesIntentoEstudianteControlador.class);
        contexto.run(c -> assertTrue(c.getBeansOfType(FasesIntentoEstudianteControlador.class).isEmpty()));
        contexto.withPropertyValues(FasesIntentoEstudianteControlador.PROPIEDAD_HTTP + "=false",
                "prisaly.m5.exploracion.fases.enabled=true")
                .run(c -> assertTrue(c.getBeansOfType(FasesIntentoEstudianteControlador.class).isEmpty()));
        contexto.withPropertyValues(FasesIntentoEstudianteControlador.PROPIEDAD_HTTP + "=true",
                "prisaly.m5.exploracion.fases.enabled=true")
                .run(c -> assertEquals(1, c.getBeansOfType(FasesIntentoEstudianteControlador.class).size()));
        contexto.withPropertyValues(FasesIntentoEstudianteControlador.PROPIEDAD_HTTP + "=true")
                .run(c -> {
                    assertTrue(c.getStartupFailure() != null, "debe fallar el arranque");
                    Throwable t = c.getStartupFailure();
                    while (t.getCause() != null) { t = t.getCause(); }
                    assertTrue(t.getMessage().startsWith(FasesIntentoEstudianteControlador.CODIGO_HTTP_SIN_FASES), t.getMessage());
                });
        var error = assertThrows(IllegalStateException.class, () -> new FasesIntentoEstudianteControlador(proxy, false));
        assertTrue(error.getMessage().startsWith(FasesIntentoEstudianteControlador.CODIGO_HTTP_SIN_FASES));
    }
}
