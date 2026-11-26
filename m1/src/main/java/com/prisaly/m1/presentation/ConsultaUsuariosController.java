package com.prisaly.m1.presentation;
import com.prisaly.m1.application.service.ConsultarUsuariosAdministrativosService;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
@RestController
@RequestMapping("/v1/admin/usuarios")
public class ConsultaUsuariosController {
    private final ConsultarUsuariosAdministrativosService servicio;
    public ConsultaUsuariosController(ConsultarUsuariosAdministrativosService servicio){this.servicio=servicio;}
    @GetMapping public ResponseEntity<ConsultarUsuariosAdministrativosService.Pagina> buscar(
            @RequestParam(defaultValue="") String q,@RequestParam(required=false) String rol,
            @RequestParam(defaultValue="25") int limite,@RequestParam(defaultValue="0") int offset) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(servicio.buscar(q,rol,limite,offset));
    }
}
