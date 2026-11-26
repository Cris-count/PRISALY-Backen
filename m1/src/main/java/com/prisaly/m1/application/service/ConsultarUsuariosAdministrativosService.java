package com.prisaly.m1.application.service;
import com.prisaly.m1.api.IdentityDomainException;
import com.prisaly.m1.application.port.ConsultaUsuariosPort;
import java.util.List;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
@Service
public class ConsultarUsuariosAdministrativosService {
    private final AdministracionIdentidadService administracion;
    private final ConsultaUsuariosPort repositorio;
    public ConsultarUsuariosAdministrativosService(AdministracionIdentidadService administracion, ConsultaUsuariosPort repositorio) {
        this.administracion=administracion; this.repositorio=repositorio;
    }
    public record Pagina(List<ConsultaUsuariosPort.UsuarioResumen> items, boolean hayMas) {}
    @Transactional(readOnly=true)
    public Pagina buscar(String texto, String rol, int limite, int offset) {
        administracion.exigirAdministrador(); // antes de cualquier acceso al catálogo o consulta
        String q=texto==null?"":texto.strip(); String filtro=rol==null||rol.isBlank()?null:rol.strip();
        if(q.length()>120 || limite<1 || limite>50 || offset<0 || offset>100000 ||
                (filtro!=null&&!Set.of("DOCENTE","ESTUDIANTE","ADMINISTRADOR").contains(filtro))) {
            throw new IdentityDomainException("ADMIN_SEARCH_INVALID","Invalid administrative search");
        }
        var rows=repositorio.buscar(q,filtro,limite+1,offset);
        return new Pagina(List.copyOf(rows.subList(0,Math.min(limite,rows.size()))), rows.size()>limite);
    }
}
