package com.prisaly.m5.presentation.rest;

import com.prisaly.m5.presentation.rest.InvestigacionM5Dto.EdificioReq;
import com.prisaly.m5.presentation.rest.InvestigacionM5Dto.EdificioRes;
import com.prisaly.m5.presentation.rest.InvestigacionM5Dto.NotaReq;
import com.prisaly.m5.presentation.rest.InvestigacionM5Dto.NotaRes;
import com.prisaly.m5.presentation.rest.InvestigacionM5Dto.PistaReq;
import com.prisaly.m5.presentation.rest.InvestigacionM5Dto.PistaRes;
import com.prisaly.m5.presentation.rest.InvestigacionM5Dto.ZonaReq;
import com.prisaly.m5.presentation.rest.InvestigacionM5Dto.ZonaRes;
import com.prisaly.m5.application.acceso.InvestigacionProtegida;
import jakarta.validation.Valid;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Contrato candidato de investigación. Depende solo de {@link InvestigacionProtegida}, que resuelve cada intento con el
 * proxy de acceso protegido (identidad estudiantil y titularidad) antes de operar (DC-111-CIERRE).
 */
@RestController
@ConditionalOnProperty(name = InvestigacionM5Controlador.PROPIEDAD, havingValue = "true")
@RequestMapping("/v1/estudiantes/intentos/{idIntento}")
public final class InvestigacionM5Controlador {

    /** Interruptor de la ruta; mismo nombre que {@code ConfiguracionParticipacion.PROPIEDAD_INVESTIGACION_HTTP}. */
    public static final String PROPIEDAD = "prisaly.m5.investigacion.http.enabled";

    private final InvestigacionProtegida investigacion;

    public InvestigacionM5Controlador(InvestigacionProtegida investigacion) {
        this.investigacion = investigacion;
    }

    @PostMapping("/notas")
    public ResponseEntity<NotaRes> guardarNota(@PathVariable UUID idIntento,
            @Valid @RequestBody NotaReq req) {
        return ResponseEntity.status(HttpStatus.CREATED).body(NotaRes.de(investigacion.guardarNota(idIntento, req.titulo(), req.texto())));
    }

    @GetMapping("/notas")
    public ResponseEntity<List<NotaRes>> listarNotas(@PathVariable UUID idIntento) {
        return ResponseEntity.ok(investigacion.notas(idIntento).stream().map(NotaRes::de).toList());
    }

    @PostMapping("/pistas")
    public ResponseEntity<PistaRes> guardarPista(@PathVariable UUID idIntento,
            @Valid @RequestBody PistaReq req) {
        return ResponseEntity.status(HttpStatus.CREATED).body(PistaRes.de(investigacion.guardarPista(idIntento, req.descripcion())));
    }

    @GetMapping("/pistas")
    public ResponseEntity<List<PistaRes>> listarPistas(@PathVariable UUID idIntento) {
        return ResponseEntity.ok(investigacion.pistas(idIntento).stream().map(PistaRes::de).toList());
    }

    @PostMapping("/edificios")
    public ResponseEntity<EdificioRes> guardarEdificio(@PathVariable UUID idIntento,
            @Valid @RequestBody EdificioReq req) {
        return ResponseEntity.status(HttpStatus.CREATED).body(EdificioRes.de(investigacion.guardarEdificio(idIntento, req.nombre(), req.ubicacion())));
    }

    @GetMapping("/edificios")
    public ResponseEntity<List<EdificioRes>> listarEdificios(@PathVariable UUID idIntento) {
        return ResponseEntity.ok(investigacion.edificios(idIntento).stream().map(EdificioRes::de).toList());
    }

    @PostMapping("/zonas")
    public ResponseEntity<ZonaRes> guardarZona(@PathVariable UUID idIntento,
            @Valid @RequestBody ZonaReq req) {
        return ResponseEntity.status(HttpStatus.CREATED).body(ZonaRes.de(investigacion.guardarZona(idIntento, req.nombre(), req.descripcion())));
    }

    @GetMapping("/zonas")
    public ResponseEntity<List<ZonaRes>> listarZonas(@PathVariable UUID idIntento) {
        return ResponseEntity.ok(investigacion.zonas(idIntento).stream().map(ZonaRes::de).toList());
    }
}
