package com.prisaly.m5.application.repeticion;

import static org.junit.jupiter.api.Assertions.*;

import com.prisaly.m3.api.AutoriaCasoNoDisponibleException;
import com.prisaly.m3.api.AutoriaCasoPort;
import com.prisaly.m4.api.ResponsableConvocatoriaPort;
import com.prisaly.m5.application.DatoAplicacionInvalidoException;
import com.prisaly.m5.application.RepeticionNoHabilitadaException;
import com.prisaly.m5.application.repeticion.AutorizacionRepeticionPort.AutorizacionRegistrada;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.Test;

/** A4 + RF-25: orquestación de la autorización de repetición con dobles en memoria (no es evidencia de BD). */
class AutorizarRepeticionServicioTest {
    final UUID creador = UUID.randomUUID(), participacion = UUID.randomUUID(), convocatoria = UUID.randomUUID();
    // RF-25: the session is programmed by a DIFFERENT teacher than the case creator.
    final UUID programador = UUID.randomUUID(), caso = UUID.randomUUID();
    final Puerto puerto = new Puerto();
    final List<UUID> casosConsultados = new ArrayList<>();
    final ResponsableConvocatoriaPort responsables = c -> c.equals(convocatoria)
            ? Optional.of(new ResponsableConvocatoriaPort.ProgramacionConvocatoria(UUID.randomUUID(), caso, programador))
            : Optional.empty();
    final AutoriaCasoPort autoria = c -> { casosConsultados.add(c); return c.equals(caso) ? Optional.of(creador) : Optional.empty(); };

    AutorizarRepeticionServicio servicio(PoliticaRepeticion politica) {
        return new AutorizarRepeticionServicio(politica, puerto, responsables, autoria);
    }

    @Test
    void elDocenteProgramadorDistintoDelCreadorNoAutoriza() {
        assertThrows(AutorizacionRepeticionDenegadaException.class,
                () -> servicio(() -> true).autorizar(programador, participacion, "m", null));
        assertEquals(List.of("bloquear"), puerto.llamadas);
        assertEquals(List.of(caso), casosConsultados, "the author is looked up for the programmed case");
    }

    @Test
    void sinAutorVerificableFallaCerrado() {
        AutoriaCasoPort sinAutor = c -> Optional.empty();
        var s = new AutorizarRepeticionServicio(() -> true, puerto, responsables, sinAutor);
        assertThrows(AutoriaCasoNoDisponibleException.class, () -> s.autorizar(creador, participacion, "m", null));
        AutoriaCasoPort caido = c -> { throw new AutoriaCasoNoDisponibleException(); };
        var t = new AutorizarRepeticionServicio(() -> true, puerto, responsables, caido);
        assertThrows(AutoriaCasoNoDisponibleException.class, () -> t.autorizar(creador, participacion, "m", null));
        assertFalse(puerto.llamadas.contains("registrar"));
        assertThrows(NullPointerException.class, () -> new AutorizarRepeticionServicio(() -> true, puerto, responsables, null));
    }

    @Test
    void conLaPoliticaPorDefectoNadaSeLeeNiSeEscribe() {
        assertFalse(new RepeticionNoRatificada().autorizacionHabilitada());
        assertThrows(RepeticionNoHabilitadaException.class,
                () -> servicio(new RepeticionNoRatificada()).autorizar(creador, participacion, "m", null));
        assertEquals(List.of(), puerto.llamadas);
    }

    @Test
    void elDocenteCreadorAutorizaTrasBloquearLaParticipacion() {
        var registrada = servicio(() -> true).autorizar(creador, participacion, "motivo", Duration.ofHours(2));
        assertEquals(creador, registrada.idDocenteAutorizador());
        assertEquals(List.of("bloquear", "existeVigente", "registrar"), puerto.llamadas);
        assertEquals(Duration.ofHours(2), puerto.vigencia);
    }

    @Test
    void otroDocenteNoAutorizaYNoSeEscribe() {
        assertThrows(AutorizacionRepeticionDenegadaException.class,
                () -> servicio(() -> true).autorizar(UUID.randomUUID(), participacion, "m", null));
        assertEquals(List.of("bloquear"), puerto.llamadas);
    }

    @Test
    void unaAutorizacionVigenteImpideOtra() {
        puerto.vigente = true;
        assertThrows(AutorizacionRepeticionVigenteException.class,
                () -> servicio(() -> true).autorizar(creador, participacion, "m", null));
        assertFalse(puerto.llamadas.contains("registrar"));
    }

    @Test
    void participacionInexistenteYDatosInvalidos() {
        assertThrows(ParticipacionNoEncontradaException.class,
                () -> servicio(() -> true).autorizar(creador, UUID.randomUUID(), "m", null));
        assertThrows(DatoAplicacionInvalidoException.class,
                () -> servicio(() -> true).autorizar(null, participacion, "m", null));
        assertThrows(DatoAplicacionInvalidoException.class,
                () -> servicio(() -> true).autorizar(creador, participacion, "m", Duration.ZERO));
        assertThrows(DatoAplicacionInvalidoException.class,
                () -> servicio(() -> true).autorizar(creador, participacion, "m", Duration.ofSeconds(-1)));
        assertFalse(puerto.llamadas.contains("registrar"));
    }

    final class Puerto implements AutorizacionRepeticionPort {
        final List<String> llamadas = new ArrayList<>();
        boolean vigente;
        Duration vigencia;

        public Optional<UUID> bloquearParticipacion(UUID id) {
            llamadas.add("bloquear");
            return id.equals(participacion) ? Optional.of(convocatoria) : Optional.empty();
        }

        public boolean existeVigente(UUID id) {
            llamadas.add("existeVigente");
            return vigente;
        }

        public AutorizacionRegistrada registrar(UUID id, UUID docente, String motivo, Duration v) {
            llamadas.add("registrar");
            vigencia = v;
            return new AutorizacionRegistrada(UUID.randomUUID(), id, docente, Optional.ofNullable(motivo),
                    Instant.parse("2026-11-13T00:00:00Z"), Optional.empty(), "Vigente");
        }
    }
}
