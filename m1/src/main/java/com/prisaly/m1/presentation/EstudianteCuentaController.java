package com.prisaly.m1.presentation;

import com.prisaly.m1.application.port.CookieSesionEstudiante;
import com.prisaly.m1.application.service.CierreSesionEstudianteService;
import com.prisaly.m1.application.service.SolicitudAccesoEstudianteService;
import com.prisaly.m1.application.service.ValidacionAccesoEstudianteService;
import com.prisaly.m1.domain.student.PoliticaSesionEstudiante;
import com.prisaly.m1.presentation.dto.ErrorResponse;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.time.Duration;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * DD-02 alternative B student access (D-B4, DC-DD02-B4-B5-01): {@code /solicitar}, {@code /validar} and
 * {@code /cerrar-sesion}. The alternative A routes ({@code /activar}, {@code /autenticar}) are retired.
 * While DD-02 is not authorized ({@code prisaly.m1.student-access.enabled=false}) every route answers 501
 * {@code ESTUDIANTE_ACCESS_PENDING_CONTRACT}. Bodies are strict ({@code additionalProperties: false}): unknown fields,
 * and any client attempt to send a student, user or session identifier, are 400. The trusted-Origin
 * check for these state-changing routes is applied by the APP security chain.
 */
@RestController
@RequestMapping("/v1/acceso-estudiante")
public class EstudianteCuentaController {

    public static final String CODE_ESTUDIANTE_ACCESS_PENDING = "ESTUDIANTE_ACCESS_PENDING_CONTRACT";
    static final String MENSAJE_SOLICITUD =
            "Si el correo corresponde a un estudiante habilitado, recibirás un código de acceso.";
    private static final Pattern CORREO = Pattern.compile("^[^@\\s]{1,64}@[^@\\s]{1,189}\\.[^@\\s]{1,63}$");

    private final boolean habilitado;
    private final boolean cookieSegura;
    private final ObjectProvider<SolicitudAccesoEstudianteService> solicitud;
    private final ObjectProvider<ValidacionAccesoEstudianteService> validacion;
    private final ObjectProvider<CierreSesionEstudianteService> cierre;

    public EstudianteCuentaController(
            @Value("${prisaly.m1.student-access.enabled:false}") boolean habilitado,
            @Value("${prisaly.m1.student-access.cookie-segura:true}") boolean cookieSegura,
            ObjectProvider<SolicitudAccesoEstudianteService> solicitud,
            ObjectProvider<ValidacionAccesoEstudianteService> validacion,
            ObjectProvider<CierreSesionEstudianteService> cierre) {
        this.habilitado = habilitado;
        this.cookieSegura = cookieSegura;
        this.solicitud = solicitud;
        this.validacion = validacion;
        this.cierre = cierre;
    }

    public record SolicitudCodigoResponse(UUID challengeId, String mensaje) {
    }

    @PostMapping("/solicitar")
    public ResponseEntity<?> solicitar(@RequestBody(required = false) Map<String, Object> cuerpo, HttpServletRequest http) {
        SolicitudAccesoEstudianteService servicio = servicio(solicitud);
        if (servicio == null) {
            return pendiente();
        }
        if (!soloCampos(cuerpo, Set.of("correo")) || !(cuerpo.get("correo") instanceof String correo)
                || correo.length() > 254 || !CORREO.matcher(correo.strip()).matches()) {
            return invalida();
        }
        UUID challengeId = servicio.solicitar(correo, http.getRemoteAddr());
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(new SolicitudCodigoResponse(challengeId, MENSAJE_SOLICITUD));
    }

    @PostMapping("/validar")
    public ResponseEntity<?> validar(@RequestBody(required = false) Map<String, Object> cuerpo, HttpServletRequest http,
            HttpServletResponse response) {
        ValidacionAccesoEstudianteService servicio = servicio(validacion);
        if (servicio == null) {
            return pendiente();
        }
        if (!soloCampos(cuerpo, Set.of("challengeId", "codigo")) || !(cuerpo.get("challengeId") instanceof String id)
                || !(cuerpo.get("codigo") instanceof String codigo) || !codigo.matches("\\d{8}")) {
            return invalida();
        }
        UUID challengeId;
        try {
            challengeId = UUID.fromString(id);
        } catch (IllegalArgumentException ex) {
            return invalida();
        }
        ValidacionAccesoEstudianteService.SesionEmitida sesion = servicio.validar(challengeId, codigo, http.getRemoteAddr());
        response.addHeader(HttpHeaders.SET_COOKIE, cookie(sesion.token(), false).toString());
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/cerrar-sesion")
    public ResponseEntity<?> cerrarSesion(HttpServletRequest http, HttpServletResponse response) {
        CierreSesionEstudianteService servicio = servicio(cierre);
        if (servicio == null) {
            return pendiente();
        }
        servicio.cerrar(leerCookie(http), http.getRemoteAddr());
        response.addHeader(HttpHeaders.SET_COOKIE, cookie("", true).toString());
        return ResponseEntity.noContent().build();
    }

    private static boolean soloCampos(Map<String, Object> cuerpo, Set<String> permitidos) {
        return cuerpo != null && permitidos.containsAll(cuerpo.keySet()) && cuerpo.keySet().containsAll(permitidos);
    }

    private <T> T servicio(ObjectProvider<T> proveedor) {
        return habilitado ? proveedor.getIfAvailable() : null;
    }

    private static ResponseEntity<ErrorResponse> pendiente() {
        return ResponseEntity.status(HttpStatus.NOT_IMPLEMENTED).body(new ErrorResponse(CODE_ESTUDIANTE_ACCESS_PENDING,
                "Student access must be coordinated with M4/M5; DD-02 is not activated"));
    }

    private static ResponseEntity<ErrorResponse> invalida() {
        return ResponseEntity.badRequest().body(new ErrorResponse("VALIDATION_ERROR", "Request validation failed"));
    }

    private ResponseCookie cookie(String token, boolean borrar) {
        return ResponseCookie.from(CookieSesionEstudiante.NOMBRE, token)
                .httpOnly(true)
                .secure(cookieSegura)
                .path("/api")
                .sameSite("Strict")
                .maxAge(borrar ? Duration.ZERO : PoliticaSesionEstudiante.VIGENCIA_MAXIMA)
                .build();
    }

    private static String leerCookie(HttpServletRequest request) {
        Cookie[] cookies = request.getCookies();
        if (cookies == null) {
            return null;
        }
        for (Cookie cookie : cookies) {
            if (CookieSesionEstudiante.NOMBRE.equals(cookie.getName())) {
                return cookie.getValue();
            }
        }
        return null;
    }
}
