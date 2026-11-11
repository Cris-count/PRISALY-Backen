package com.prisaly.m1.infrastructure.security;

import com.prisaly.m1.api.IdentityDomainException;
import com.prisaly.m1.application.service.InicializacionAdministradorService;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * P5 / DM1-01 (DC-P5-FASE-B-01): startup-only first-administrator initialization. Registered only when
 * {@code prisaly.m1.inicializacion-admin.habilitada=true}; there is no endpoint.
 * <p>The password is read once from {@code archivo-credencial}. It is never logged, returned or kept after the call:
 * logs carry only the outcome, the error code and the administrator id. The application keeps running on failure
 * (without administrator). Deleting the credential file afterwards is part of the operating procedure.
 */
@Component
@ConditionalOnProperty(prefix = "prisaly.m1.inicializacion-admin", name = "habilitada", havingValue = "true")
public class InicializacionAdministradorRunner implements ApplicationRunner {

    public static final String LOGGER = "prisaly.auditoria.seguridad";
    static final String ORIGEN = "ARRANQUE";
    private static final Logger log = LoggerFactory.getLogger(LOGGER);

    private final InicializacionAdministradorService servicio;
    private final String correo;
    private final String nombre;
    private final String archivoCredencial;

    public InicializacionAdministradorRunner(InicializacionAdministradorService servicio,
            @Value("${prisaly.m1.inicializacion-admin.correo:}") String correo,
            @Value("${prisaly.m1.inicializacion-admin.nombre:}") String nombre,
            @Value("${prisaly.m1.inicializacion-admin.archivo-credencial:}") String archivoCredencial) {
        this.servicio = servicio;
        this.correo = correo;
        this.nombre = nombre;
        this.archivoCredencial = archivoCredencial;
    }

    @Override
    public void run(ApplicationArguments args) {
        ejecutar();
    }

    /** @return the outcome label written to the security log (never contains a credential). */
    public String ejecutar() {
        String contrasena;
        try {
            contrasena = leerCredencial();
        } catch (IOException | IllegalStateException ex) {
            return registrar("RECHAZADO", "CREDENTIAL_FILE_UNREADABLE", null);
        }
        try {
            InicializacionAdministradorService.Resultado r = servicio.inicializar(nombre, correo, contrasena, ORIGEN);
            if (r.estado() == InicializacionAdministradorService.Estado.YA_REALIZADA) {
                return registrar("SIN_CAMBIOS", "INICIALIZACION_YA_REALIZADA", null);
            }
            return registrar("EXITOSO", null, r.idUsuarioAdministrador());
        } catch (IdentityDomainException ex) {
            return registrar("RECHAZADO", ex.code(), null);
        } catch (RuntimeException ex) {
            // Exception messages may carry SQL values: only the type is logged.
            return registrar("RECHAZADO", ex.getClass().getSimpleName(), null);
        }
    }

    private String leerCredencial() throws IOException {
        if (archivoCredencial == null || archivoCredencial.isBlank()) {
            throw new IllegalStateException("no credential file");
        }
        String contenido = Files.readString(Path.of(archivoCredencial), StandardCharsets.UTF_8);
        // Only the line terminator of a one-line file is removed; inner or leading spaces are part of the secret.
        String contrasena = contenido.replaceFirst("\\r?\\n\\z", "");
        if (contrasena.isEmpty() || contrasena.contains("\n")) {
            throw new IllegalStateException("credential file must contain exactly one line");
        }
        return contrasena;
    }

    private static String registrar(String resultado, String codigo, java.util.UUID idUsuario) {
        String linea = "SEGURIDAD_ADMINISTRACION evento=INICIALIZACION resultado=" + resultado
                + (codigo == null ? "" : " codigo=" + codigo) + (idUsuario == null ? "" : " usuario=" + idUsuario);
        if ("EXITOSO".equals(resultado) || "SIN_CAMBIOS".equals(resultado)) {
            log.info(linea);
        } else {
            log.warn(linea);
        }
        return linea;
    }
}
