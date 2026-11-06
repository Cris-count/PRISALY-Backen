package com.prisaly.m5.presentation.rest;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import com.prisaly.m5.application.OperacionesParticipacion;
import com.prisaly.m5.application.ReferenciaConvocatoria;
import com.prisaly.m5.application.ReferenciaIntento;
import com.prisaly.m5.application.ResultadoInicio;
import com.prisaly.m5.application.ResumenIntento;
import com.prisaly.m5.application.acceso.AccesoProtegidoParticipacion;
import com.prisaly.m5.application.acceso.ControlAccesoIntentos;
import com.prisaly.m5.application.acceso.IdentificadorEstudiante;
import com.prisaly.m5.application.acceso.IdentidadEstudianteActual;
import com.prisaly.m5.application.acceso.Titularidad;
import com.prisaly.m5.application.acceso.TitularidadIntentos;
import com.prisaly.m5.domain.intento.MotivoFinalizacion;
import com.prisaly.m5.domain.respuesta.ReferenciaPregunta;
import com.prisaly.m5.domain.respuesta.Respuesta;
import com.prisaly.m5.domain.respuesta.ValorRespuesta;
import com.prisaly.m5.presentation.ManejadorErroresM5;

/**
 * Despacho MVC real (DispatcherServlet de prueba, conversión de rutas, validación y
 * {@link ManejadorErroresM5}) de las rutas GET/PUT registradas, con el proxy real. La cadena de
 * Spring Security de la aplicación se prueba en {@code app} ({@code EstudianteIntentosM5SecurityIT}).
 */
@DisplayName("IntentosEstudianteM5Controlador: despacho MVC con rechazo seguro")
class IntentosEstudianteM5MvcTest {

    private static final String INTENTO = UUID.randomUUID().toString();
    private static final String PREGUNTA = UUID.randomUUID().toString();
    private static final String CUERPO = "{\"idOpcionSeleccionada\":\"" + UUID.randomUUID() + "\",\"revisionEsperada\":0}";

    private final List<String> llamadas = new ArrayList<>();

    private MockMvc mvc(IdentidadEstudianteActual identidad, Titularidad titularidad) {
        TitularidadIntentos t = new TitularidadIntentos() {
            @Override
            public Titularidad de(ReferenciaIntento intento, IdentificadorEstudiante estudiante) {
                llamadas.add("titularidad");
                return titularidad;
            }

            @Override
            public Titularidad deConvocatoria(ReferenciaConvocatoria c, IdentificadorEstudiante e) {
                return Titularidad.AJENO;
            }
        };
        var proxy = new AccesoProtegidoParticipacion(new ControlAccesoIntentos(identidad, t), new Registro());
        return MockMvcBuilders.standaloneSetup(new IntentosEstudianteM5Controlador(proxy))
                .setControllerAdvice(new ManejadorErroresM5()).build();
    }

    private static void sinDatosPrivados(MvcResult r) throws Exception {
        String cuerpo = r.getResponse().getContentAsString();
        assertFalse(cuerpo.contains(INTENTO), cuerpo);
        assertFalse(cuerpo.contains(PREGUNTA), cuerpo);
        assertFalse(cuerpo.toLowerCase().contains("exception"), cuerpo);
        assertFalse(cuerpo.toLowerCase().contains("sql"), cuerpo);
        assertFalse(cuerpo.contains("idEstudiante"), cuerpo);
    }

    @Test
    @DisplayName("sin identidad estudiantil: GET 403 STUDENT_IDENTITY_UNAVAILABLE, sin titularidad ni servicio")
    void getSinIdentidad() throws Exception {
        MvcResult r = mvc(Optional::empty, Titularidad.PROPIO).perform(get("/v1/estudiantes/intentos/" + INTENTO))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("STUDENT_IDENTITY_UNAVAILABLE")).andReturn();
        sinDatosPrivados(r);
        assertTrue(llamadas.isEmpty(), llamadas.toString());
    }

    @Test
    @DisplayName("sin identidad estudiantil: PUT 403, sin titularidad ni escritura")
    void putSinIdentidad() throws Exception {
        MvcResult r = mvc(Optional::empty, Titularidad.PROPIO)
                .perform(put("/v1/estudiantes/intentos/" + INTENTO + "/respuestas/" + PREGUNTA)
                        .contentType(MediaType.APPLICATION_JSON).content(CUERPO))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("STUDENT_IDENTITY_UNAVAILABLE")).andReturn();
        sinDatosPrivados(r);
        assertTrue(llamadas.isEmpty(), llamadas.toString());
    }

    @Test
    @DisplayName("intento ajeno: GET y PUT 403 FORBIDDEN sin revelar titular y sin invocar el servicio")
    void ajeno() throws Exception {
        IdentidadEstudianteActual ana = () -> Optional.of(new IdentificadorEstudiante("E-ana"));
        MockMvc mvc = mvc(ana, Titularidad.AJENO);
        sinDatosPrivados(mvc.perform(get("/v1/estudiantes/intentos/" + INTENTO))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("FORBIDDEN")).andReturn());
        sinDatosPrivados(mvc.perform(put("/v1/estudiantes/intentos/" + INTENTO + "/respuestas/" + PREGUNTA)
                        .contentType(MediaType.APPLICATION_JSON).content(CUERPO))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("FORBIDDEN")).andReturn());
        assertEquals(List.of("titularidad", "titularidad"), llamadas);
    }

    @Test
    @DisplayName("UUID inválido o cuerpo inválido: 400 VALIDATION_ERROR sin llegar al proxy")
    void validacion() throws Exception {
        MockMvc mvc = mvc(Optional::empty, Titularidad.PROPIO);
        mvc.perform(get("/v1/estudiantes/intentos/no-es-uuid"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
        mvc.perform(put("/v1/estudiantes/intentos/no-es-uuid/respuestas/" + PREGUNTA)
                        .contentType(MediaType.APPLICATION_JSON).content(CUERPO))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
        mvc.perform(put("/v1/estudiantes/intentos/" + INTENTO + "/respuestas/" + PREGUNTA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"idOpcionSeleccionada\":\"" + UUID.randomUUID() + "\",\"revisionEsperada\":-1}"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
        mvc.perform(put("/v1/estudiantes/intentos/" + INTENTO + "/respuestas/" + PREGUNTA)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"revisionEsperada\":0}"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
        assertTrue(llamadas.isEmpty(), llamadas.toString());
    }

    @Test
    @DisplayName("los POST no están mapeados en este controlador")
    void postNoMapeado() throws Exception {
        mvc(Optional::empty, Titularidad.PROPIO)
                .perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .post("/v1/estudiantes/intentos/" + INTENTO + "/finalizar"))
                .andExpect(status().is4xxClientError());
        assertTrue(llamadas.isEmpty(), llamadas.toString());
    }

    private final class Registro implements OperacionesParticipacion {
        private AssertionError invocado(String op) {
            llamadas.add(op);
            return new AssertionError("servicio de participación invocado: " + op);
        }

        @Override
        public ResultadoInicio iniciarOReanudar(ReferenciaConvocatoria c) { throw invocado("iniciar"); }

        @Override
        public ResumenIntento consultarIntento(ReferenciaIntento i) { throw invocado("consultar"); }

        @Override
        public ResumenIntento registrarRespuesta(ReferenciaIntento i, ReferenciaPregunta p, ValorRespuesta v) {
            throw invocado("registrar");
        }

        @Override
        public ResumenIntento actualizarRespuesta(ReferenciaIntento i, ReferenciaPregunta p, ValorRespuesta v) {
            throw invocado("actualizar");
        }

        @Override
        public Respuesta guardarRespuesta(ReferenciaIntento i, ReferenciaPregunta p, ValorRespuesta v, int r) {
            throw invocado("guardar");
        }

        @Override
        public ResumenIntento finalizarIntento(ReferenciaIntento i, MotivoFinalizacion m) { throw invocado("finalizar"); }
    }
}
