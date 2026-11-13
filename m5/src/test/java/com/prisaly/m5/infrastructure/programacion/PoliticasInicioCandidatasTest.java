package com.prisaly.m5.infrastructure.programacion;

import static org.junit.jupiter.api.Assertions.*;

import com.prisaly.m4.api.ContextoProgramacion;
import com.prisaly.m4.api.ProgramacionAutenticadaPort;
import com.prisaly.m5.application.ReferenciaConvocatoria;
import com.prisaly.m5.application.inicio.ElegibilidadInicio.Resultado;
import com.prisaly.m5.infrastructure.ConfiguracionParticipacion;

import java.lang.annotation.Annotation;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.Test;

/** CIERRE-07 P3: candidate start policies over the M4 contract. Candidates, not ratified rules, and not assembled. */
class PoliticasInicioCandidatasTest {
    static final Instant APERTURA = Instant.parse("2026-11-13T14:00:00Z");
    final UUID convocatoria = UUID.randomUUID();

    ContextoProgramacion contexto(String sesion, String autorizacion, Optional<Instant> retiro, String caso, boolean activo,
            Duration maximo, Instant finPrevisto) {
        return new ContextoProgramacion(convocatoria, UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), sesion,
                autorizacion, retiro, caso, activo, APERTURA, finPrevisto, Optional.of(APERTURA), Optional.empty(),
                maximo, Duration.ofMinutes(20), APERTURA);
    }

    ContextoProgramacion valido() {
        return contexto("EnCurso", "Autorizado", Optional.empty(), "Publicado", true, Duration.ofMinutes(30),
                APERTURA.plusSeconds(7200));
    }

    ProgramacionAutenticadaPort devuelve(Optional<ContextoProgramacion> c) {
        return new ProgramacionAutenticadaPort() {
            public Optional<ContextoProgramacion> consultarSesion(UUID s) { throw new AssertionError("not used"); }
            public Optional<ContextoProgramacion> consultarConvocatoria(UUID id) {
                assertEquals(convocatoria, id);
                return c;
            }
        };
    }

    ReferenciaConvocatoria ref() { return new ReferenciaConvocatoria(convocatoria.toString()); }

    @Test
    void elegibleSoloConSesionEnCursoConvocatoriaVigenteYCasoPublicadoActivo() {
        assertEquals(Resultado.ELEGIBLE, new ElegibilidadCandidataDesdeM4(devuelve(Optional.of(valido()))).evaluar(ref()));
        var noElegibles = new ContextoProgramacion[] {
            contexto("SinIniciar", "Autorizado", Optional.empty(), "Publicado", true, Duration.ofMinutes(30), APERTURA.plusSeconds(7200)),
            contexto("Finalizado", "Autorizado", Optional.empty(), "Publicado", true, Duration.ofMinutes(30), APERTURA.plusSeconds(7200)),
            contexto("EnCurso", "Retirado", Optional.of(APERTURA), "Publicado", true, Duration.ofMinutes(30), APERTURA.plusSeconds(7200)),
            contexto("EnCurso", "Autorizado", Optional.of(APERTURA), "Publicado", true, Duration.ofMinutes(30), APERTURA.plusSeconds(7200)),
            contexto("EnCurso", "Autorizado", Optional.empty(), "Archivado", true, Duration.ofMinutes(30), APERTURA.plusSeconds(7200)),
            contexto("EnCurso", "Autorizado", Optional.empty(), "Publicado", false, Duration.ofMinutes(30), APERTURA.plusSeconds(7200))};
        for (var c : noElegibles) {
            assertEquals(Resultado.NO_ELEGIBLE, new ElegibilidadCandidataDesdeM4(devuelve(Optional.of(c))).evaluar(ref()), c.toString());
        }
        assertEquals(Resultado.NO_ELEGIBLE, new ElegibilidadCandidataDesdeM4(devuelve(Optional.empty())).evaluar(ref()));
        assertEquals(Resultado.NO_ELEGIBLE, new ElegibilidadCandidataDesdeM4(devuelve(Optional.empty()))
                .evaluar(new ReferenciaConvocatoria("no-es-uuid")));
    }

    @Test
    void sinIngresoTardioLaHoraDeInicioNoCambiaLaElegibilidad() {
        // RF-22 not ratified: a context observed long after the real start is still eligible.
        var tarde = new ContextoProgramacion(convocatoria, UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), "EnCurso",
                "Autorizado", Optional.empty(), "Publicado", true, APERTURA, APERTURA.plusSeconds(7200),
                Optional.of(APERTURA), Optional.empty(), Duration.ofMinutes(30), Duration.ofMinutes(1),
                APERTURA.plusSeconds(7000));
        assertEquals(Resultado.ELEGIBLE, new ElegibilidadCandidataDesdeM4(devuelve(Optional.of(tarde))).evaluar(ref()));
    }

    @Test
    void elFalloTecnicoDeM4SePropaga() {
        ProgramacionAutenticadaPort caido = new ProgramacionAutenticadaPort() {
            public Optional<ContextoProgramacion> consultarSesion(UUID s) { throw new IllegalStateException(); }
            public Optional<ContextoProgramacion> consultarConvocatoria(UUID c) {
                throw new com.prisaly.m4.api.ConsultaProgramacionNoDisponibleException();
            }
        };
        assertThrows(com.prisaly.m4.api.ConsultaProgramacionNoDisponibleException.class,
                () -> new ElegibilidadCandidataDesdeM4(caido).evaluar(ref()));
        assertThrows(com.prisaly.m4.api.ConsultaProgramacionNoDisponibleException.class,
                () -> new LimiteIndividualCandidatoDesdeM4(caido).calcular(ref(), APERTURA));
    }

    @Test
    void limiteIndividualEsInicioMasMaximoAcotadoPorElFinPrevisto() {
        var limite = new LimiteIndividualCandidatoDesdeM4(devuelve(Optional.of(valido())));
        Instant inicio = APERTURA.plusSeconds(600);
        assertEquals(Optional.of(inicio.plus(Duration.ofMinutes(30))), limite.calcular(ref(), inicio));
        // Started 100 minutes in: 30 more minutes would pass the 120-minute planned end, so the end bounds it.
        Instant tarde = APERTURA.plusSeconds(6000);
        assertEquals(Optional.of(APERTURA.plusSeconds(7200)), limite.calcular(ref(), tarde));
        // At or after the planned end there is no valid limit (ck_intento_tiempos): no attempt is created.
        assertEquals(Optional.empty(), limite.calcular(ref(), APERTURA.plusSeconds(7200)));
        assertEquals(Optional.empty(), limite.calcular(ref(), null));
        assertEquals(Optional.empty(), new LimiteIndividualCandidatoDesdeM4(devuelve(Optional.empty())).calcular(ref(), inicio));
    }

    @Test
    void lasCandidatasNoSeEnsamblanNiSonComponentes() throws Exception {
        for (Class<?> tipo : new Class<?>[] {ElegibilidadCandidataDesdeM4.class, LimiteIndividualCandidatoDesdeM4.class}) {
            for (Annotation a : tipo.getAnnotations()) {
                assertFalse(a.annotationType().getName().startsWith("org.springframework"), tipo + " " + a);
            }
        }
        Path fuente = Path.of("src/main/java", ConfiguracionParticipacion.class.getName().replace('.', '/') + ".java");
        if (!Files.exists(fuente)) { fuente = Path.of("m5").resolve(fuente); }
        String ensamblado = Files.readString(fuente, StandardCharsets.UTF_8);
        assertTrue(ensamblado.contains("new ElegibilidadNoRatificada()"));
        assertTrue(ensamblado.contains("new LimiteTemporalNoRatificado()"));
        assertFalse(ensamblado.contains("Candidat"), "production assembly must not use the candidates");
    }
}
