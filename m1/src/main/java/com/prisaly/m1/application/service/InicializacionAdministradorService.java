package com.prisaly.m1.application.service;

import com.prisaly.m1.api.IdentityDomainException;
import com.prisaly.m1.application.port.InicializacionAdministradorPort;
import com.prisaly.m1.application.port.RolRepositorioPort;
import com.prisaly.m1.domain.model.Usuario;
import com.prisaly.m1.domain.rule.PoliticaPermisos;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * P5 / DM1-01 alternativa C (DC-P5-FASE-B-01): one-shot creation of the first ADMINISTRADOR. There is no HTTP route.
 * <p>Everything happens in one transaction: advisory lock, marker check, user registration (password policy DM1-08
 * and BCrypt via {@link IdentityCatalogService#registerUser}) and the immutable V013 marker. Any failure rolls back
 * both the user and the marker. The ADMINISTRADOR role is never created here: PD-01 is its only source (D-03 c).
 */
@Service
public class InicializacionAdministradorService {

    public static final String CODE_ADMIN_ROLE_NOT_PROVISIONED = "ADMIN_ROLE_NOT_PROVISIONED";
    public static final String CODE_INITIALIZATION_DATA_MISSING = "INITIALIZATION_DATA_MISSING";
    public static final int ORIGEN_MAXIMO = 40;

    /** Outcome without any credential: the id is null when nothing was created. */
    public record Resultado(Estado estado, UUID idUsuarioAdministrador) { }

    public enum Estado { INICIALIZADO, YA_REALIZADA }

    private final InicializacionAdministradorPort marcador;
    private final RolRepositorioPort roles;
    private final IdentityCatalogService catalogo;

    public InicializacionAdministradorService(InicializacionAdministradorPort marcador, RolRepositorioPort roles,
            IdentityCatalogService catalogo) {
        this.marcador = marcador;
        this.roles = roles;
        this.catalogo = catalogo;
    }

    @Transactional
    public Resultado inicializar(String nombre, String correo, String contrasena, String origen) {
        if (vacio(nombre) || vacio(correo) || contrasena == null || vacio(origen) || origen.length() > ORIGEN_MAXIMO) {
            throw new IdentityDomainException(CODE_INITIALIZATION_DATA_MISSING, "Initialization data missing");
        }
        marcador.bloquear();
        if (marcador.marcadorExiste()) {
            return new Resultado(Estado.YA_REALIZADA, null);
        }
        if (roles.buscarPorNombre(PoliticaPermisos.ROL_ADMINISTRADOR).isEmpty()) {
            throw new IdentityDomainException(CODE_ADMIN_ROLE_NOT_PROVISIONED, "ADMINISTRADOR role is not provisioned");
        }
        Usuario administrador = catalogo.registerUser(nombre.trim(), correo, contrasena, PoliticaPermisos.ROL_ADMINISTRADOR);
        marcador.registrarMarcador(administrador.getIdUsuario(), origen);
        return new Resultado(Estado.INICIALIZADO, administrador.getIdUsuario());
    }

    private static boolean vacio(String valor) {
        return valor == null || valor.isBlank();
    }
}
