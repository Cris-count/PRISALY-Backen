package com.prisaly.m5.presentation.rest;

import com.prisaly.m5.application.ReferenciaIntento;
import com.prisaly.m5.application.acceso.AccesoProtegidoParticipacion;
import com.prisaly.m5.application.ContenidoIntento;
import java.util.UUID;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/v1/estudiantes/intentos")
public class ContenidoIntentoEstudianteControlador {
    private final AccesoProtegidoParticipacion consulta;
    public ContenidoIntentoEstudianteControlador(AccesoProtegidoParticipacion consulta) { this.consulta = consulta; }

    @GetMapping("/{idIntento}/contenido")
    public ResponseEntity<ContenidoIntento> consultar(@PathVariable("idIntento") UUID idIntento) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .body(consulta.consultarContenido(new ReferenciaIntento(idIntento.toString())));
    }

}
