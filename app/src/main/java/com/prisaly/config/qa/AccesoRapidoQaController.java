package com.prisaly.config.qa;

import jakarta.servlet.http.HttpServletRequest;
import java.io.IOException;
import java.net.InetAddress;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.env.Environment;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import tools.jackson.databind.ObjectMapper;

/**
 * DC-115 · «Acceso rápido QA». NO es una puerta trasera: no emite sesiones por sí mismo ni omite ninguna comprobación.
 * Automatiza en el servidor exactamente lo que haría una persona con las cuentas ficticias del QA:
 * <ul>
 *   <li>Personal: llama al login oficial {@code POST /v1/personal/autenticar} con la contraseña real de la cuenta QA,
 *       leída de un secreto del servidor (archivo montado), nunca de Angular. El servidor sigue verificando la
 *       contraseña, los límites y los permisos.</li>
 *   <li>Estudiante: ejecuta el flujo oficial DD-02 B (solicitar código → leerlo del buzón QA de Mailpit → validar) y
 *       reenvía al navegador la cookie de sesión M1 emitida por el propio M1.</li>
 * </ul>
 * Salvaguardas: desactivado por defecto; el arranque falla con un perfil productivo o con cookie segura; solo atiende
 * peticiones desde loopback o red privada (el QA publica su puerto solo en 127.0.0.1) y con el Origin configurado;
 * solo opera sobre las tres cuentas {@code @prisaly.test} configuradas.
 */
@RestController
@ConditionalOnProperty(name = AccesoRapidoQaController.PROPIEDAD, havingValue = "true")
@RequestMapping("/v1/qa/acceso-rapido")
public class AccesoRapidoQaController {

    public static final String PROPIEDAD = "prisaly.qa.acceso-rapido.enabled";
    private static final Pattern CODIGO = Pattern.compile("\\b(\\d{8})\\b");
    private static final Pattern CHALLENGE = Pattern.compile("\"challengeId\"\\s*:\\s*\"([0-9a-fA-F-]{36})\"");

    private final String origen;
    private final String apiInterna;
    private final String mailpitApi;
    private final Map<String, Cuenta> personal;
    private final String correoEstudiante;
    private final ObjectMapper json;
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();

    record Cuenta(String correo, Path archivoClave, String rolEsperado) { }

    public AccesoRapidoQaController(
            Environment environment,
            ObjectMapper json,
            @Value("${prisaly.qa.acceso-rapido.origen}") String origen,
            @Value("${prisaly.qa.acceso-rapido.api-interna:http://127.0.0.1:${APP_PORT:8080}/api}") String apiInterna,
            @Value("${prisaly.qa.acceso-rapido.mailpit-api}") String mailpitApi,
            @Value("${prisaly.qa.acceso-rapido.administrador.correo}") String correoAdmin,
            @Value("${prisaly.qa.acceso-rapido.administrador.archivo-clave}") String claveAdmin,
            @Value("${prisaly.qa.acceso-rapido.docente.correo}") String correoDocente,
            @Value("${prisaly.qa.acceso-rapido.docente.archivo-clave}") String claveDocente,
            @Value("${prisaly.qa.acceso-rapido.estudiante.correo}") String correoEstudiante,
            @Value("${prisaly.m1.student-access.cookie-segura:true}") boolean cookieSegura) {
        List<String> perfiles = Arrays.stream(environment.getActiveProfiles()).map(String::toLowerCase).toList();
        if (perfiles.stream().anyMatch(p -> p.contains("prod"))) {
            throw new IllegalStateException("QA-ACCESO-RAPIDO-PROHIBIDO-EN-PRODUCCION: perfil " + perfiles);
        }
        if (cookieSegura) {
            throw new IllegalStateException("QA-ACCESO-RAPIDO-SOLO-QA-LOCAL: requiere prisaly.m1.student-access.cookie-segura=false (HTTP local)");
        }
        URI uriOrigen = URI.create(origen);
        if (!esLocal(uriOrigen.getHost())) {
            throw new IllegalStateException("QA-ACCESO-RAPIDO-ORIGEN-NO-LOCAL: " + origen);
        }
        for (String correo : List.of(correoAdmin, correoDocente, correoEstudiante)) {
            if (!correo.toLowerCase().endsWith("@prisaly.test")) {
                throw new IllegalStateException("QA-ACCESO-RAPIDO-CUENTA-NO-FICTICIA: " + correo);
            }
        }
        this.json = json;
        this.origen = origen;
        this.apiInterna = apiInterna;
        this.mailpitApi = mailpitApi;
        this.correoEstudiante = correoEstudiante;
        this.personal = Map.of(
                "administrador", new Cuenta(correoAdmin, Path.of(claveAdmin), "ADMINISTRADOR"),
                "docente", new Cuenta(correoDocente, Path.of(claveDocente), "DOCENTE"));
    }

    private static boolean esLocal(String host) {
        return "localhost".equalsIgnoreCase(host) || "127.0.0.1".equals(host) || "[::1]".equals(host);
    }

    /** Solo loopback o red privada del contenedor (el proxy del QA) y con el Origin configurado. */
    private ResponseEntity<Map<String, String>> rechazo(HttpServletRequest request) {
        try {
            InetAddress remoto = InetAddress.getByName(request.getRemoteAddr());
            if (!(remoto.isLoopbackAddress() || remoto.isSiteLocalAddress())) {
                return error(403, "QA_ACCESO_SOLO_LOCAL");
            }
        } catch (IOException e) {
            return error(403, "QA_ACCESO_SOLO_LOCAL");
        }
        if (!origen.equals(request.getHeader(HttpHeaders.ORIGIN))) {
            return error(403, "QA_ACCESO_ORIGEN_NO_PERMITIDO");
        }
        return null;
    }

    @PostMapping("/personal/{rol}")
    public ResponseEntity<?> personal(@PathVariable("rol") String rol, HttpServletRequest request) throws Exception {
        var r = rechazo(request);
        if (r != null) return r;
        Cuenta cuenta = personal.get(rol);
        if (cuenta == null) return error(404, "QA_ROL_NO_DISPONIBLE");
        if (!Files.isReadable(cuenta.archivoClave())) return error(503, "QA_CREDENCIAL_NO_CONFIGURADA");
        String clave = Files.readString(cuenta.archivoClave(), StandardCharsets.UTF_8).strip();
        if (clave.isEmpty()) return error(503, "QA_CREDENCIAL_NO_CONFIGURADA");
        String cuerpo = json.writeValueAsString(Map.of("correo", cuenta.correo(), "password", clave));
        HttpResponse<String> login = http.send(post("/v1/personal/autenticar", cuerpo, null), HttpResponse.BodyHandlers.ofString());
        if (login.statusCode() != 200) return error(login.statusCode() == 429 ? 429 : 502, "QA_LOGIN_OFICIAL_RECHAZADO");
        var datos = json.readTree(login.body());
        if (!cuenta.rolEsperado().equals(datos.path("rol").asString(""))) return error(409, "QA_ROL_INESPERADO");
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).contentType(MediaType.APPLICATION_JSON).body(login.body());
    }

    @PostMapping("/estudiante")
    public ResponseEntity<?> estudiante(HttpServletRequest request) throws Exception {
        var r = rechazo(request);
        if (r != null) return r;
        long desde = System.currentTimeMillis() - 2000;
        String cuerpo = json.writeValueAsString(Map.of("correo", correoEstudiante));
        HttpResponse<String> solicitud = http.send(post("/v1/acceso-estudiante/solicitar", cuerpo, null), HttpResponse.BodyHandlers.ofString());
        Matcher ch = CHALLENGE.matcher(solicitud.body());
        // El límite oficial por cuenta (DD-02 B) se respeta: se informa, nunca se elude.
        if (solicitud.statusCode() == 429) return error(429, "QA_LIMITE_OFICIAL_DE_CODIGOS");
        if (solicitud.statusCode() != 202 || !ch.find()) return error(502, "QA_SOLICITUD_OFICIAL_RECHAZADA");
        String codigo = esperarCodigo(desde);
        if (codigo == null) return error(504, "QA_CODIGO_NO_RECIBIDO");
        String validar = json.writeValueAsString(Map.of("challengeId", ch.group(1), "codigo", codigo));
        HttpResponse<String> validacion = http.send(post("/v1/acceso-estudiante/validar", validar, null), HttpResponse.BodyHandlers.ofString());
        List<String> cookies = validacion.headers().allValues(HttpHeaders.SET_COOKIE);
        if (validacion.statusCode() != 204 || cookies.isEmpty()) return error(502, "QA_VALIDACION_OFICIAL_RECHAZADA");
        var respuesta = ResponseEntity.noContent().cacheControl(CacheControl.noStore());
        for (String c : cookies) respuesta.header(HttpHeaders.SET_COOKIE, c);
        return respuesta.build();
    }

    /** Lee el código del buzón QA (Mailpit), igual que lo haría la persona que prueba. */
    private String esperarCodigo(long desde) throws Exception {
        String busqueda = mailpitApi + "/api/v1/search?query=" + URLEncoder.encode("to:" + correoEstudiante, StandardCharsets.UTF_8);
        for (int i = 0; i < 40; i++) {
            var lista = json.readTree(http.send(HttpRequest.newBuilder(URI.create(busqueda)).GET().build(),
                    HttpResponse.BodyHandlers.ofString()).body()).path("messages");
            for (var m : lista) {
                long creado = java.time.OffsetDateTime.parse(m.path("Created").asString("1970-01-01T00:00:00Z")).toInstant().toEpochMilli();
                if (creado < desde) continue;
                String id = m.path("ID").asString("");
                String texto = json.readTree(http.send(HttpRequest.newBuilder(URI.create(mailpitApi + "/api/v1/message/" + id)).GET().build(),
                        HttpResponse.BodyHandlers.ofString()).body()).path("Text").asString("");
                Matcher c = CODIGO.matcher(texto);
                if (c.find()) return c.group(1);
            }
            Thread.sleep(500);
        }
        return null;
    }

    private HttpRequest post(String ruta, String cuerpo, String cookie) {
        var b = HttpRequest.newBuilder(URI.create(apiInterna + ruta))
                .timeout(Duration.ofSeconds(15))
                .header("Content-Type", "application/json")
                .header("Origin", origen)
                .POST(HttpRequest.BodyPublishers.ofString(cuerpo));
        if (cookie != null) b.header("Cookie", cookie);
        return b.build();
    }

    private static ResponseEntity<Map<String, String>> error(int estado, String codigo) {
        return ResponseEntity.status(estado).cacheControl(CacheControl.noStore()).body(Map.of("code", codigo));
    }
}
