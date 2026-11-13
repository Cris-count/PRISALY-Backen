package com.prisaly.m5.presentation.rest;

import com.prisaly.m5.application.ContenidoIntentoNoDisponibleException;
import com.prisaly.m5.application.ReferenciaIntento;
import com.prisaly.m5.application.port.ConsultaContenidoIntento;
import com.prisaly.m5.application.port.ConsultaContenidoIntento.Contenido;
import java.util.UUID;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/v1/estudiantes/intentos")
public class ContenidoIntentoEstudianteControlador {
    private final ConsultaContenidoIntento consulta;
    public ContenidoIntentoEstudianteControlador(ConsultaContenidoIntento consulta) { this.consulta = consulta; }

    @GetMapping("/{idIntento}/contenido")
    public ResponseEntity<Contenido> consultar(@PathVariable("idIntento") UUID idIntento) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .body(consulta.consultar(new ReferenciaIntento(idIntento.toString())));
    }

    @ExceptionHandler(ContenidoIntentoNoDisponibleException.class)
    public ResponseEntity<ProblemDetail> noDisponible() {
        var detalle = ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, "Contenido académico no disponible para esta lectura");
        detalle.setProperty("code", "CONTENIDO_INTENTO_NO_DISPONIBLE");
        return ResponseEntity.status(HttpStatus.CONFLICT).cacheControl(CacheControl.noStore()).body(detalle);
    }
}
