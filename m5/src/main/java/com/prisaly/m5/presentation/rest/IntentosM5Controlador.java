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
import org.springframework.web.bind.annotation.RestController;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;

/**
 * Adaptador REST preparatorio de los POST de M5 (iniciar/reanudar y finalizar), según
 * {@code CONTRATO_REST_M5.yaml} bajo el contexto {@code /api}. GET y PUT están en
 * {@link IntentosEstudianteM5Controlador}, que sí está registrado (DC-DD02-01).
 *
 * <p><b>Ensamblaje preparado, no publicado</b> (DC-BE-CIERRE-FUNCIONAL-09, fase 3). La clase solo se registra
 * con {@code prisaly.m5.inicio-finalizacion.publicacion.enabled=true}; el valor productivo es {@code false},
 * así que por defecto los POST no existen (404/405). Aun publicados, siguen exigiendo la identidad estudiantil
 * real de M1 (DD-02) y la titularidad, y el primer inicio sigue bloqueado mientras el ensamblado de
 * producción use {@code ElegibilidadNoRatificada} y {@code LimiteTemporalNoRatificado} (respuesta 409
 * {@code INICIO_NO_DISPONIBLE}). La finalización repetida con el mismo motivo es idempotente (D13/C3).
 * No debe activarse sin la autorización de las reglas de elegibilidad y tiempo individual.
 *
 * <p>Solo depende del proxy {@link AccesoProtegidoParticipacion}. Los errores los traduce
 * {@code ManejadorErroresM5} con el cuerpo común {@code {code, message}} (D09).
 */
@RestController
@ConditionalOnProperty(name = IntentosM5Controlador.PROPIEDAD_PUBLICACION, havingValue = "true")
@RequestMapping("/v1/estudiantes")
public final class IntentosM5Controlador {

    /** Interruptor de publicación de los POST; {@code false} en {@code application.yml}. */
    public static final String PROPIEDAD_PUBLICACION = "prisaly.m5.inicio-finalizacion.publicacion.enabled";

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

    /** D08: cierre voluntario; el motivo lo asigna el servidor. Repetirlo devuelve el mismo cierre (D13/C3). */
    @PostMapping("/intentos/{idIntento}/finalizar")
    public Finalizacion finalizar(@PathVariable("idIntento") UUID idIntento) {
        return Finalizacion.de(operaciones.finalizarIntento(
                new ReferenciaIntento(idIntento.toString()), MotivoFinalizacion.ENTREGA_ESTUDIANTE));
    }
}
