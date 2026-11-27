package com.prisaly.m1.application;
import com.prisaly.m1.api.IdentityDomainException;
import com.prisaly.m1.application.port.ConsultaUsuariosPort;
import com.prisaly.m1.application.service.*;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
class ConsultarUsuariosAdministrativosServiceTest {
 @Test void rechazaAntesDeConsultarCuandoNoEsAdministrador(){
  var admin=mock(AdministracionIdentidadService.class);var repo=mock(ConsultaUsuariosPort.class);
  when(admin.exigirAdministrador()).thenThrow(new IdentityDomainException("ADMIN_REQUIRED","Administrator required"));
  assertThrows(IdentityDomainException.class,()->new ConsultarUsuariosAdministrativosService(admin,repo).buscar("","DOCENTE",25,0));verifyNoInteractions(repo);
 }
 @Test void limitaPaginaYNormalizaBusqueda(){
  var admin=mock(AdministracionIdentidadService.class);var repo=mock(ConsultaUsuariosPort.class);
  var u=new ConsultaUsuariosPort.UsuarioResumen(UUID.randomUUID(),null,"Nombre","qa@prisaly.test","DOCENTE","Activo",false,"NO_EMITIDA");
  when(repo.buscar("nombre","DOCENTE",2,0)).thenReturn(List.of(u,u));
  var result=new ConsultarUsuariosAdministrativosService(admin,repo).buscar(" nombre ","DOCENTE",1,0);
  assertEquals(1,result.items().size());assertTrue(result.hayMas());var order=inOrder(admin,repo);order.verify(admin).exigirAdministrador();order.verify(repo).buscar("nombre","DOCENTE",2,0);
 }
 @Test void rechazaFiltrosInvalidosSinConsultar(){
  var admin=mock(AdministracionIdentidadService.class);var repo=mock(ConsultaUsuariosPort.class);var s=new ConsultarUsuariosAdministrativosService(admin,repo);
  assertThrows(IdentityDomainException.class,()->s.buscar("","OTRO",25,0));assertThrows(IdentityDomainException.class,()->s.buscar("",null,51,0));assertThrows(IdentityDomainException.class,()->s.buscar("x".repeat(121),null,25,0));assertThrows(IdentityDomainException.class,()->s.buscar("",null,1,-1));verifyNoInteractions(repo);
 }
}
