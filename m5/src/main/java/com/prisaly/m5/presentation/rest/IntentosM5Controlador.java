package com.prisaly.m5.presentation.rest;

import java.util.UUID;

import com.prisaly.m5.application.ReferenciaIntento;
import com.prisaly.m5.application.ReferenciaSesion;
import com.prisaly.m5.application.acceso.AccesoProtegidoParticipacion;
import com.prisaly.m5.domain.intento.MotivoFinalizacion;
import com.prisaly.m5.presentation.rest.IntentosM5Dto.Finalizacion;
import com.prisaly.m5.presentation.rest.IntentosM5Dto.Inicio;
import com.prisaly.m5.presentation.rest.IntentosM5Dto.Intento;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;

/**
 * Adaptador REST preparatorio de los POST de M5 (iniciar/reanudar y finalizar), según
 * {@code CONTRATO_REST_M5.yaml} bajo el contexto {@code /api}. GET y PUT están en
 * {@link IntentosEstudianteM5Controlador}, que sí está registrado (DC-DD02-01).
 *
 * <p><b>Sin registro MVC.</b> La clase no está anotada como {@code @RestController} ni se declara
 * como bean: los POST no quedan publicados. Exigen la identidad estudiantil real de M1 (D03/DD-02),
 * D02 y la idempotencia durable de D07, y el primer inicio, la ratificación de elegibilidad y límite
 * temporal. No debe registrarse para hacer pasar pruebas.
 *
 * <p>Solo depende del proxy {@link AccesoProtegidoParticipacion}. Los errores los traduce
 * {@code ManejadorErroresM5} con el cuerpo común {@code {code, message}} (D09).
 */
@RequestMapping("/v1/estudiantes")
public final class IntentosM5Controlador {

    private final AccesoProtegidoParticipacion operaciones;

    public IntentosM5Controlador(AccesoProtegidoParticipacion operaciones) {
        if (operaciones == null) {
            throw new IllegalArgumentException("El controlador requiere el acceso protegido de M5");
        }
        this.operaciones = operaciones;
    }

    /** D01 y D04: 201 al crear y 200 al reanudar; los tiempos son los persistidos. */
    @PostMapping("/sesiones/{idSesion}/intentos")
    public ResponseEntity<Inicio> iniciar(@PathVariable("idSesion") UUID idSesion) {
        var inicio = operaciones.iniciarOReanudarEnSesion(new ReferenciaSesion(idSesion.toString()));
        return ResponseEntity.status(inicio.reanudado() ? HttpStatus.OK : HttpStatus.CREATED)
                .body(new Inicio(Intento.de(inicio.intento()), inicio.reanudado()));
    }

    /** D08: cierre voluntario; el motivo lo asigna el servidor. D07 (idempotencia) sigue pendiente. */
    @PostMapping("/intentos/{idIntento}/finalizar")
    public Finalizacion finalizar(@PathVariable("idIntento") UUID idIntento) {
        return Finalizacion.de(operaciones.finalizarIntento(
                new ReferenciaIntento(idIntento.toString()), MotivoFinalizacion.ENTREGA_ESTUDIANTE));
    }
}
