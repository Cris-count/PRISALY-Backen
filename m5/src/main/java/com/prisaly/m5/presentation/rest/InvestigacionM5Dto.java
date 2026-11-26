package com.prisaly.m5.presentation.rest;

import com.prisaly.m5.domain.investigacion.Edificio;
import com.prisaly.m5.domain.investigacion.Nota;
import com.prisaly.m5.domain.investigacion.Pista;
import com.prisaly.m5.domain.investigacion.Zona;
import jakarta.validation.constraints.NotBlank;
import java.time.Instant;
import java.util.UUID;

/** DTOs de investigación M5: sin números de promedio ni notas de evaluación (Espectros M5). */
public final class InvestigacionM5Dto {
    private InvestigacionM5Dto() {}

    public record NotaReq(@NotBlank String titulo, @NotBlank String texto) {}

    public record NotaRes(UUID idNota, UUID idIntento, String titulo, String texto, Instant fecha) {
        public static NotaRes de(Nota n) { return new NotaRes(n.idNota(), n.idIntento(), n.titulo(), n.texto(), n.fechaCreacion()); }
    }

    public record PistaReq(@NotBlank String descripcion) {}

    public record PistaRes(UUID idPista, UUID idIntento, String descripcion, Instant fecha) {
        public static PistaRes de(Pista p) { return new PistaRes(p.idPista(), p.idIntento(), p.descripcion(), p.fechaCreacion()); }
    }

    public record EdificioReq(@NotBlank String nombre, String ubicacion) {}

    public record EdificioRes(UUID idEdificio, UUID idIntento, String nombre, String ubicacion, Instant fecha) {
        public static EdificioRes de(Edificio e) { return new EdificioRes(e.idEdificio(), e.idIntento(), e.nombre(), e.ubicacion(), e.fechaCreacion()); }
    }

    public record ZonaReq(@NotBlank String nombre, String descripcion) {}

    public record ZonaRes(UUID idZona, UUID idIntento, String nombre, String descripcion, Instant fecha) {
        public static ZonaRes de(Zona z) { return new ZonaRes(z.idZona(), z.idIntento(), z.nombre(), z.descripcion(), z.fechaCreacion()); }
    }
}
