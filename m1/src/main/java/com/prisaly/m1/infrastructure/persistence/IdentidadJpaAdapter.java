package com.prisaly.m1.infrastructure.persistence;

import com.prisaly.m1.application.port.AsignacionPermisoRepositorioPort;
import com.prisaly.m1.application.port.PermisoRepositorioPort;
import com.prisaly.m1.application.port.RolRepositorioPort;
import com.prisaly.m1.application.port.UsuarioRepositorioPort;
import com.prisaly.m1.domain.model.AsignacionPermiso;
import com.prisaly.m1.domain.model.Permiso;
import com.prisaly.m1.domain.model.Rol;
import com.prisaly.m1.domain.model.Usuario;
import java.time.OffsetDateTime;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Component;

/** Traduce el modelo de M1 hacia sus entidades JPA. Los errores de integridad se propagan. */
@Component
public class IdentidadJpaAdapter implements UsuarioRepositorioPort, RolRepositorioPort, PermisoRepositorioPort,
        AsignacionPermisoRepositorioPort {

    private final UsuarioRepository usuarios;
    private final RolRepository roles;
    private final PermisoRepository permisos;
    private final UsuarioPermisoRepository asignaciones;

    public IdentidadJpaAdapter(UsuarioRepository usuarios, RolRepository roles, PermisoRepository permisos,
            UsuarioPermisoRepository asignaciones) {
        this.usuarios = usuarios;
        this.roles = roles;
        this.permisos = permisos;
        this.asignaciones = asignaciones;
    }

    @Override
    public Optional<Usuario> buscarPorId(UUID idUsuario) {
        return usuarios.findById(idUsuario).map(IdentidadJpaAdapter::usuario);
    }

    @Override
    public Optional<Usuario> buscarPorCorreo(String correo) {
        return usuarios.findByCorreoIgnoreCase(correo).map(IdentidadJpaAdapter::usuario);
    }

    @Override
    public boolean existePorId(UUID idUsuario) {
        return usuarios.existsById(idUsuario);
    }

    @Override
    public boolean existePorCorreo(String correo) {
        return usuarios.existsByCorreoIgnoreCase(correo);
    }

    @Override
    public Usuario registrar(Usuario usuario) {
        RolEntity rol = roles.getReferenceById(usuario.getRol().getIdRol());
        return usuario(usuarios.saveAndFlush(new UsuarioEntity(usuario.getIdUsuario(), rol, usuario.getNombre(),
                usuario.getCorreo(), usuario.getPasswordHash(), usuario.getEstado(), usuario.getFechaCreacion())));
    }

    @Override
    public void cambiarEstado(UUID idUsuario, String estado, OffsetDateTime fecha) {
        UsuarioEntity usuario = usuarios.findById(idUsuario).orElseThrow();
        usuario.changeEstado(estado, fecha);
        usuarios.saveAndFlush(usuario);
    }

    @Override
    public void actualizarPasswordHash(UUID idUsuario, String passwordHash, OffsetDateTime fecha) {
        UsuarioEntity usuario = usuarios.findById(idUsuario).orElseThrow();
        usuario.replacePasswordHash(passwordHash, fecha);
        usuarios.saveAndFlush(usuario);
    }

    @Override
    public List<Rol> listarRoles() {
        return roles.findAll(Sort.by("idRol")).stream().map(IdentidadJpaAdapter::rol).toList();
    }

    @Override
    public List<Permiso> listarPermisos() {
        return permisos.findAll(Sort.by("idPermiso")).stream().map(IdentidadJpaAdapter::permiso).toList();
    }

    @Override
    public boolean esCuentaEstudiante(UUID idUsuario) {
        return usuarios.esCuentaEstudiante(idUsuario);
    }

    @Override
    public Optional<Rol> buscarPorNombre(String nombre) {
        return roles.findByNombre(nombre).map(IdentidadJpaAdapter::rol);
    }

    @Override
    public Rol registrar(String nombre, String descripcion) {
        return rol(roles.saveAndFlush(new RolEntity(nombre, descripcion)));
    }

    @Override
    public Optional<Permiso> buscarPorId(Integer idPermiso) {
        return permisos.findById(idPermiso).map(IdentidadJpaAdapter::permiso);
    }

    @Override
    public Optional<Permiso> buscarPorCodigo(String codigo) {
        return permisos.findByCodigo(codigo).map(IdentidadJpaAdapter::permiso);
    }

    @Override
    public boolean existePorId(Integer idPermiso) {
        return permisos.existsById(idPermiso);
    }

    @Override
    public Permiso registrar(String codigo, String nombre, String descripcion) {
        return permiso(permisos.saveAndFlush(new PermisoEntity(codigo, nombre, descripcion)));
    }

    @Override
    public Set<String> codigosHabilitados(UUID idUsuario) {
        Set<String> codigos = new LinkedHashSet<>();
        for (UsuarioPermisoEntity asignacion : asignaciones.findByIdUsuarioAndHabilitadoTrue(idUsuario)) {
            if (asignacion.getPermiso() != null) {
                codigos.add(asignacion.getPermiso().getCodigo());
            }
        }
        return codigos;
    }

    @Override
    public boolean existe(UUID idUsuario, Integer idPermiso) {
        return asignaciones.existsByIdUsuarioAndIdPermiso(idUsuario, idPermiso);
    }

    @Override
    public AsignacionPermiso registrar(UUID idUsuario, Integer idPermiso, UUID idAsignador, OffsetDateTime fecha) {
        UsuarioPermisoEntity guardada = asignaciones.saveAndFlush(
                new UsuarioPermisoEntity(idUsuario, idPermiso, true, idAsignador, fecha));
        return new AsignacionPermiso(guardada.getIdUsuario(), guardada.getIdPermiso(), guardada.isHabilitado(),
                guardada.getIdAdministradorAsignador(), guardada.getFechaAsignacion());
    }

    @Override
    public boolean deshabilitar(UUID idUsuario, Integer idPermiso) {
        Optional<UsuarioPermisoEntity> asignacion = asignaciones.findByIdUsuarioAndIdPermiso(idUsuario, idPermiso)
                .filter(UsuarioPermisoEntity::isHabilitado);
        asignacion.ifPresent(a -> {
            a.setHabilitado(false);
            asignaciones.saveAndFlush(a);
        });
        return asignacion.isPresent();
    }

    @Override
    public boolean existeHabilitada(UUID idUsuario, Integer idPermiso) {
        return asignaciones.findByIdUsuarioAndIdPermiso(idUsuario, idPermiso)
                .map(UsuarioPermisoEntity::isHabilitado).orElse(false);
    }

    @Override
    public AsignacionPermiso reactivar(UUID idUsuario, Integer idPermiso, UUID idAsignador, OffsetDateTime fecha) {
        UsuarioPermisoEntity asignacion = asignaciones.findByIdUsuarioAndIdPermiso(idUsuario, idPermiso).orElseThrow();
        asignacion.reactivar(idAsignador, fecha);
        UsuarioPermisoEntity guardada = asignaciones.saveAndFlush(asignacion);
        return new AsignacionPermiso(guardada.getIdUsuario(), guardada.getIdPermiso(), guardada.isHabilitado(),
                guardada.getIdAdministradorAsignador(), guardada.getFechaAsignacion());
    }

    private static Rol rol(RolEntity e) {
        return new Rol(e.getIdRol(), e.getNombre(), e.getDescripcion());
    }

    private static Permiso permiso(PermisoEntity e) {
        return new Permiso(e.getIdPermiso(), e.getCodigo(), e.getNombre(), e.getDescripcion());
    }

    private static Usuario usuario(UsuarioEntity e) {
        return new Usuario(e.getIdUsuario(), rol(e.getRol()), e.getNombre(), e.getCorreo(), e.getPasswordHash(),
                e.getEstado(), e.getFechaCreacion(), e.getFechaActualizacion());
    }
}
