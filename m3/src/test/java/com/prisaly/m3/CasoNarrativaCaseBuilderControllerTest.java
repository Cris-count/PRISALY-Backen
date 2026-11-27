package com.prisaly.m3;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import com.prisaly.m3.application.port.CaseAuthorizationPort;
import com.prisaly.m3.application.port.GuionNarrativoRepositorio;
import com.prisaly.m3.application.port.UnidadTransaccionalNarrativa;
import com.prisaly.m3.application.service.GuionNarrativoService;
import com.prisaly.m3.domain.narrativa.ValidadorGuionNarrativo;
import com.prisaly.m3.infrastructure.config.CatalogoVisualRecurso;
import com.prisaly.m3.infrastructure.persistence.jpa.JsonbCanonicalizer;
import com.prisaly.m3.presentation.CasoNarrativaController;
import com.prisaly.m3.presentation.CasoNarrativaExceptionHandler;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Supplier;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

/**
 * CB-02 / CB-05A (DC-CB-03, DC-CB-04): recorrido HTTP «Hospital + médico» por el controlador, el servicio y el validador
 * reales con el catálogo canónico. Cubre los niveles 1 (validación M3) y 2 (respuesta HTTP). El repositorio es un doble
 * en memoria con la misma huella canónica que el adaptador JDBC: <b>no</b> es evidencia de persistencia en PostgreSQL.
 */
class CasoNarrativaCaseBuilderControllerTest {
    static final ObjectMapper JSON = new ObjectMapper();
    final UUID docente = UUID.randomUUID(), caso = UUID.randomUUID();
    final Memoria repo = new Memoria();
    MockMvc mvc;

    @BeforeEach
    void setup() {
        CaseAuthorizationPort permisos = new CaseAuthorizationPort() {
            public boolean canCreateCase(String u, String d) { return true; }
            public boolean canModifyCase(String u, String c) { return docente.toString().equals(u); }
            public boolean canPublishCase(String u, String c) { return docente.toString().equals(u); }
        };
        UnidadTransaccionalNarrativa unidad = new UnidadTransaccionalNarrativa() {
            public <T> T ejecutar(Supplier<T> c) { return c.get(); }
        };
        var servicio = new GuionNarrativoService(() -> docente, permisos, repo, unidad,
                new ValidadorGuionNarrativo(CatalogoVisualRecurso.cargarVigente()));
        mvc = MockMvcBuilders.standaloneSetup(new CasoNarrativaController(servicio))
                .setControllerAdvice(new CasoNarrativaExceptionHandler()).build();
    }

    static String recurso(String nombre) throws IOException {
        try (InputStream in = CasoNarrativaCaseBuilderControllerTest.class.getClassLoader().getResourceAsStream("case-builder/" + nombre)) {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    JsonNode guardar(String documento, int estado) throws Exception {
        String cuerpo = mvc.perform(put("/v1/casos/{id}/narrativa", caso).contentType(MediaType.APPLICATION_JSON)
                        .content(documento.getBytes(StandardCharsets.UTF_8)))
                .andExpect(status().is(estado)).andExpect(header().string("Cache-Control", "no-store"))
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        return JSON.readTree(cuerpo);
    }

    JsonNode consultar() throws Exception {
        return JSON.readTree(mvc.perform(get("/v1/casos/{id}/narrativa", caso)).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8));
    }

    @Test
    void hospitalYMedicoSeGuardanYSeRecuperanIgualQueSeEnviaron() throws Exception {
        String enviado = recurso("guion-meta-1-hospital-medico.json");
        JsonNode guardado = guardar(enviado, 200);
        assertEquals("Borrador", guardado.get("estado").asText());
        assertEquals(1, guardado.get("version").asInt());
        assertEquals(huella(enviado), guardado.get("hashDefinicion").asText());

        JsonNode vista = consultar();
        JsonNode borrador = vista.get("borrador");
        assertTrue(vista.get("publicado").isNull());
        assertEquals(JSON.readTree(enviado), borrador.get("definicion"), "la narrativa recuperada es la enviada");
        assertEquals(guardado.get("hashDefinicion"), borrador.get("hashDefinicion"));
        assertEquals("Durante la valoración se observaron dificultades emocionales relacionadas con su entorno familiar.",
                borrador.at("/definicion/intervenciones/0/dialogos/1/texto").asText());
        assertEquals("npc:hosp-npc-doctor", borrador.at("/definicion/personajes/0/recursoVisual").asText());
    }

    @Test
    void editarElDialogoReemplazaElBorradorSinCrearOtraVersion() throws Exception {
        String original = recurso("guion-meta-1-hospital-medico.json");
        guardar(original, 200);
        String editado = original.replace("dificultades emocionales relacionadas con su entorno familiar",
                "señales de aislamiento social en su comunidad");
        JsonNode segundo = guardar(editado, 200);
        assertEquals(1, segundo.get("version").asInt());
        assertEquals(JSON.readTree(editado), consultar().get("borrador").get("definicion"));
    }

    @Test
    void unaConfiguracionIncompatibleSeRechazaCon422YNoSeGuarda() throws Exception {
        String hospital = recurso("guion-meta-1-hospital-medico.json");
        guardar(hospital, 200);
        String oficialEnHospital = hospital.replace("npc:hosp-npc-doctor", "npc:pol-npc-oficial");
        JsonNode error = guardar(oficialEnHospital, 422);
        assertEquals("GUION_INVALIDO", error.get("code").asText());
        String errores = error.get("errores").toString();
        assertTrue(errores.contains("EDIFICIO_NO_SELECCIONADO"), errores);
        assertTrue(errores.contains("PERSONAJE_FUERA_DE_SU_EDIFICIO"), errores);
        assertEquals(JSON.readTree(hospital), consultar().get("borrador").get("definicion"), "el borrador anterior sigue intacto");
    }

    @Test
    void unRecursoFueraDelCatalogoSeRechaza() throws Exception {
        String inexistente = recurso("guion-meta-1-hospital-medico.json").replace("npc:hosp-npc-doctor", "npc:hosp-npc-cirujano");
        assertTrue(guardar(inexistente, 422).get("errores").toString().contains("RECURSO_VISUAL_NO_CATALOGADO"));
        assertNull(repo.borrador);
    }

    static String huella(String json) throws Exception {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                .digest(JsonbCanonicalizer.canonicalize(json).getBytes(StandardCharsets.UTF_8)));
    }

    /** Doble en memoria: reemplaza el borrador como el adaptador JDBC y usa su misma huella canónica. */
    final class Memoria implements GuionNarrativoRepositorio {
        GuionGuardado borrador;

        public Optional<CasoNarrativa> bloquearCaso(UUID id) {
            return Optional.of(new CasoNarrativa(id, docente, "Borrador", true, false));
        }
        public Optional<GuionGuardado> borrador(UUID id) { return Optional.ofNullable(borrador); }
        public Optional<GuionGuardado> publicadoVigente(UUID id) { return Optional.empty(); }
        public GuionGuardado guardarBorrador(UUID id, UUID autor, String doc) {
            try {
                UUID idGuion = borrador == null ? UUID.randomUUID() : borrador.idGuion();
                borrador = new GuionGuardado(idGuion, id, 1, "Borrador", doc, huella(doc), autor, Instant.now(), Optional.empty());
                return borrador;
            } catch (Exception e) {
                throw new IllegalStateException(e);
            }
        }
        public GuionGuardado publicarBorrador(UUID id) { throw new UnsupportedOperationException(); }
    }
}
