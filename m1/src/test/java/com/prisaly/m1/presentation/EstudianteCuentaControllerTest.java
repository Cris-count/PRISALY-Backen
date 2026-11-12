package com.prisaly.m1.presentation;

import static org.hamcrest.Matchers.containsString;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.prisaly.m1.application.port.CodigoAccesoDeliveryCommand;
import com.prisaly.m1.application.service.CierreSesionEstudianteService;
import com.prisaly.m1.application.service.SolicitudAccesoEstudianteService;
import com.prisaly.m1.application.service.ValidacionAccesoEstudianteService;
import com.prisaly.m1.application.support.AccesoEstudianteMemoria;
import com.prisaly.m1.application.support.ClavesDePrueba;
import com.prisaly.m1.domain.student.ClavesAccesoEstudiante;
import com.prisaly.m1.infrastructure.security.LimiteAccesoEstudianteEnMemoria;
import java.time.Clock;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.support.SimpleTransactionStatus;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.ObjectMapper;

/** D-B4 contract at MVC level (Origin and CSRF boundary are proven in the APP security tests and HTTP IT). */
class EstudianteCuentaControllerTest {

    private final ObjectMapper json = new ObjectMapper();
    private AccesoEstudianteMemoria accesos;
    private List<CodigoAccesoDeliveryCommand> entregados;
    private MockMvc mvc;

    private static <T> ObjectProvider<T> proveedor(Supplier<T> s) {
        return new ObjectProvider<>() {
            @Override public T getObject(Object... args) { return s.get(); }
            @Override public T getIfAvailable() { return s.get(); }
            @Override public T getIfUnique() { return s.get(); }
            @Override public T getObject() { return s.get(); }
        };
    }

    @BeforeEach
    void preparar() {
        accesos = new AccesoEstudianteMemoria();
        entregados = new ArrayList<>();
        ClavesAccesoEstudiante claves = ClavesDePrueba.claves();
        var limites = new LimiteAccesoEstudianteEnMemoria(5, 20, 20, Duration.ofMinutes(15), Clock.systemUTC());
        TransactionTemplate sinTx = new TransactionTemplate() {
            @Override public <T> T execute(org.springframework.transaction.support.TransactionCallback<T> a) {
                return a.doInTransaction(new SimpleTransactionStatus());
            }
        };
        var solicitud = new SolicitudAccesoEstudianteService(accesos, limites, entregados::add, claves, sinTx, Clock.systemUTC());
        var validacion = new ValidacionAccesoEstudianteService(accesos, limites, claves);
        var cierre = new CierreSesionEstudianteService(accesos, claves);
        mvc = MockMvcBuilders.standaloneSetup(new EstudianteCuentaController(true, true,
                        proveedor(() -> solicitud), proveedor(() -> validacion), proveedor(() -> cierre)))
                .setControllerAdvice(new LimiteAccesoEstudianteExceptionHandler(), new IdentityExceptionHandler())
                .build();
    }

    private org.springframework.test.web.servlet.ResultActions solicitar(String cuerpo) throws Exception {
        return mvc.perform(post("/v1/acceso-estudiante/solicitar").contentType(MediaType.APPLICATION_JSON).content(cuerpo));
    }

    @Test
    void solicitarRespondeLoMismoExistaONoLaCuenta() throws Exception {
        accesos.alta("est@prisaly.test", true);
        String existe = solicitar("{\"correo\":\"est@prisaly.test\"}").andExpect(status().isAccepted())
                .andReturn().getResponse().getContentAsString();
        String noExiste = solicitar("{\"correo\":\"nadie@prisaly.test\"}").andExpect(status().isAccepted())
                .andReturn().getResponse().getContentAsString();
        var a = json.readTree(existe);
        var b = json.readTree(noExiste);
        assertEquals(2, a.size());
        assertEquals(2, b.size());
        assertEquals(a.get("mensaje").asText(), b.get("mensaje").asText());
        assertEquals(36, a.get("challengeId").asText().length());
        assertEquals(36, b.get("challengeId").asText().length());
        assertEquals(1, entregados.size(), "only the registered account receives a code");
    }

    @Test
    void cuerposEstrictosRechazanCamposExtraOMalFormados() throws Exception {
        for (String cuerpo : List.of("{}", "{\"correo\":\"est@prisaly.test\",\"studentId\":\"x\"}",
                "{\"correo\":\"no-es-correo\"}", "{\"correo\":123}", "no-json")) {
            solicitar(cuerpo).andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
        }
        for (String cuerpo : List.of("{\"challengeId\":\"x\",\"codigo\":\"12345678\"}",
                "{\"challengeId\":\"8f0d1b2c-1111-4222-8333-444455556666\",\"codigo\":\"123\"}",
                "{\"challengeId\":\"8f0d1b2c-1111-4222-8333-444455556666\",\"codigo\":\"12345678\",\"idSesion\":\"y\"}")) {
            mvc.perform(post("/v1/acceso-estudiante/validar").contentType(MediaType.APPLICATION_JSON).content(cuerpo))
                    .andExpect(status().isBadRequest());
        }
    }

    @Test
    void validarEntregaCookieSeguraYCualquierFalloEs401Uniforme() throws Exception {
        accesos.alta("est@prisaly.test", true);
        String id = json.readTree(solicitar("{\"correo\":\"est@prisaly.test\"}").andReturn().getResponse()
                .getContentAsString()).get("challengeId").asText();
        String codigo = entregados.get(0).codigoTemporal();
        var r = mvc.perform(post("/v1/acceso-estudiante/validar").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"challengeId\":\"" + id + "\",\"codigo\":\"" + codigo + "\"}"))
                .andExpect(status().isNoContent())
                .andExpect(header().string("Set-Cookie", containsString("prisaly_estudiante=")))
                .andExpect(header().string("Set-Cookie", containsString("HttpOnly")))
                .andExpect(header().string("Set-Cookie", containsString("Secure")))
                .andExpect(header().string("Set-Cookie", containsString("SameSite=Strict")))
                .andExpect(header().string("Set-Cookie", containsString("Path=/api")))
                .andExpect(header().string("Set-Cookie", containsString("Max-Age=28800")))
                .andReturn();
        assertFalse(r.getResponse().getContentAsString().contains(codigo));
        String reutilizado = mvc.perform(post("/v1/acceso-estudiante/validar").contentType(MediaType.APPLICATION_JSON)
                .content("{\"challengeId\":\"" + id + "\",\"codigo\":\"" + codigo + "\"}"))
                .andExpect(status().isUnauthorized()).andReturn().getResponse().getContentAsString();
        String desconocido = mvc.perform(post("/v1/acceso-estudiante/validar").contentType(MediaType.APPLICATION_JSON)
                .content("{\"challengeId\":\"8f0d1b2c-1111-4222-8333-444455556666\",\"codigo\":\"12345678\"}"))
                .andExpect(status().isUnauthorized()).andReturn().getResponse().getContentAsString();
        assertEquals(reutilizado, desconocido);
    }

    @Test
    void reenvioInmediatoEs429ConRetryAfterYMismoCuerpoParaCualquierCorreo() throws Exception {
        accesos.alta("est@prisaly.test", true);
        solicitar("{\"correo\":\"est@prisaly.test\"}");
        solicitar("{\"correo\":\"nadie@prisaly.test\"}");
        String a = solicitar("{\"correo\":\"est@prisaly.test\"}").andExpect(status().isTooManyRequests())
                .andExpect(header().exists("Retry-After")).andReturn().getResponse().getContentAsString();
        String b = solicitar("{\"correo\":\"nadie@prisaly.test\"}").andExpect(status().isTooManyRequests())
                .andExpect(header().exists("Retry-After")).andReturn().getResponse().getContentAsString();
        assertEquals(a, b);
    }

    @Test
    void cerrarSesionEsIdempotenteYBorraLaCookie() throws Exception {
        mvc.perform(post("/v1/acceso-estudiante/cerrar-sesion")).andExpect(status().isNoContent())
                .andExpect(header().string("Set-Cookie", containsString("Max-Age=0")));
    }

    @Test
    void conElAccesoDesactivadoLasTresRutasResponden501() throws Exception {
        var apagado = MockMvcBuilders.standaloneSetup(new EstudianteCuentaController(false, true,
                proveedor(() -> null), proveedor(() -> null), proveedor(() -> null))).build();
        for (String ruta : List.of("solicitar", "validar", "cerrar-sesion")) {
            apagado.perform(post("/v1/acceso-estudiante/" + ruta).contentType(MediaType.APPLICATION_JSON).content("{}"))
                    .andExpect(status().isNotImplemented())
                    .andExpect(jsonPath("$.code").value(EstudianteCuentaController.CODE_ESTUDIANTE_ACCESS_PENDING));
        }
        for (String retirada : List.of("activar", "autenticar")) {
            apagado.perform(post("/v1/acceso-estudiante/" + retirada).contentType(MediaType.APPLICATION_JSON).content("{}"))
                    .andExpect(status().isNotFound());
        }
    }
}
