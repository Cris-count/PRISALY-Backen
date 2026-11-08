package com.prisaly.m1.presentation;

import com.prisaly.m1.application.service.ActivacionCuentaEstudianteService;
import com.prisaly.m1.application.service.AutenticacionEstudianteService;
import com.prisaly.m1.infrastructure.security.EstudianteSessionFilter;
import com.prisaly.m1.presentation.dto.ActivarCuentaEstudianteRequest;
import com.prisaly.m1.presentation.dto.AutenticarEstudianteRequest;
import com.prisaly.m1.presentation.dto.ErrorResponse;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import java.time.Duration;
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
 * Alta y sesión de la cuenta propia. Con el flag apagado responde 501 y no autentica.
 */
@RestController
@RequestMapping("/v1/acceso-estudiante")
public class EstudianteCuentaController {

    private final boolean studentAccessEnabled;
    private final ObjectProvider<ActivacionCuentaEstudianteService> activacion;
    private final ObjectProvider<AutenticacionEstudianteService> autenticacion;

    public EstudianteCuentaController(
            @Value("${prisaly.m1.student-access.enabled:false}") boolean studentAccessEnabled,
            ObjectProvider<ActivacionCuentaEstudianteService> activacion,
            ObjectProvider<AutenticacionEstudianteService> autenticacion) {
        this.studentAccessEnabled = studentAccessEnabled;
        this.activacion = activacion;
        this.autenticacion = autenticacion;
    }

    @PostMapping("/activar")
    public ResponseEntity<?> activar(@Valid @RequestBody ActivarCuentaEstudianteRequest request) {
        ActivacionCuentaEstudianteService servicio = servicio(activacion);
        if (servicio == null) {
            return pendiente();
        }
        servicio.activar(request.challengeId(), request.codigo(), request.contrasena());
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/autenticar")
    public ResponseEntity<?> autenticar(
            @Valid @RequestBody AutenticarEstudianteRequest request,
            HttpServletRequest http,
            HttpServletResponse response) {
        AutenticacionEstudianteService servicio = servicio(autenticacion);
        if (servicio == null) {
            return pendiente();
        }
        AutenticacionEstudianteService.SesionEmitida sesion =
                servicio.autenticar(request.correo(), request.contrasena());
        response.addHeader(HttpHeaders.SET_COOKIE, cookie(sesion.token(), http.isSecure(), false).toString());
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/cerrar-sesion")
    public ResponseEntity<?> cerrarSesion(HttpServletRequest http, HttpServletResponse response) {
        AutenticacionEstudianteService servicio = servicio(autenticacion);
        if (servicio == null) {
            return pendiente();
        }
        servicio.revocar(leerCookie(http));
        response.addHeader(HttpHeaders.SET_COOKIE, cookie("", http.isSecure(), true).toString());
        return ResponseEntity.noContent().build();
    }

    private <T> T servicio(ObjectProvider<T> proveedor) {
        if (!studentAccessEnabled) {
            return null;
        }
        return proveedor.getIfAvailable();
    }

    private static ResponseEntity<ErrorResponse> pendiente() {
        return ResponseEntity.status(HttpStatus.NOT_IMPLEMENTED)
                .body(new ErrorResponse(
                        EstudianteAccessController.CODE_ESTUDIANTE_ACCESS_PENDING,
                        "Student access must be coordinated with M4/M5; DD-02 is not activated"));
    }

    private static ResponseCookie cookie(String token, boolean secure, boolean borrar) {
        return ResponseCookie.from(EstudianteSessionFilter.COOKIE, token)
                .httpOnly(true)
                .secure(secure)
                .path("/api")
                .sameSite("Strict")
                .maxAge(borrar ? Duration.ZERO : AutenticacionEstudianteService.VIGENCIA_SESION)
                .build();
    }

    private static String leerCookie(HttpServletRequest request) {
        Cookie[] cookies = request.getCookies();
        if (cookies == null) {
            return null;
        }
        for (Cookie cookie : cookies) {
            if (EstudianteSessionFilter.COOKIE.equals(cookie.getName())) {
                return cookie.getValue();
            }
        }
        return null;
    }
}
