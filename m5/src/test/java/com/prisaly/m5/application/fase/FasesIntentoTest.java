package com.prisaly.m5.application.fase;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.prisaly.m3.api.ContenidoCasoEstudiantilPort;
import com.prisaly.m5.application.ContenidoIntento;
import com.prisaly.m5.application.IntentoNoEncontradoException;
import com.prisaly.m5.application.LimiteTemporalVencidoException;
import com.prisaly.m5.application.ReferenciaConvocatoria;
import com.prisaly.m5.application.ReferenciaIntento;
import com.prisaly.m5.application.ResultadoInicio;
import com.prisaly.m5.application.ResumenIntento;
import com.prisaly.m5.application.port.GuionesDelIntento;
import com.prisaly.m5.application.port.RepositorioFases;
import com.prisaly.m5.domain.intento.EstadoIntento;
import com.prisaly.m5.domain.intento.IntentoFinalizadoException;
import com.prisaly.m5.domain.respuesta.ReferenciaPregunta;
import com.prisaly.m5.domain.respuesta.Respuesta;
import com.prisaly.m5.domain.respuesta.ValorRespuesta;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** DC-BE-SIM-B1-B2A-24: reglas estructurales de B2a con dobles en memoria (sin base de datos ni Spring). */
@DisplayName("Fases académicas del intento (B2a): reglas estructurales")
class FasesIntentoTest {

    static final Instant T0 = Instant.parse("2026-11-15T15:00:00Z");
    final UUID idIntento = UUID.randomUUID();
    final ReferenciaIntento intento = new ReferenciaIntento(idIntento.toString());
    final UUID guion = UUID.randomUUID();

    /** Doble de la persistencia: una fila por intento; registra las escrituras. */
    static final class Fases implements RepositorioFases {
        final Map<String, IntentoConFase> filas = new HashMap<>();
        final List<String> escrituras = new ArrayList<>();
        int bloqueos;
        Instant ahora = T0;

        @Override public Optional<IntentoConFase> leer(ReferenciaIntento i) { return Optional.ofNullable(filas.get(i.valor())); }
        @Override public Optional<IntentoConFase> bloquear(ReferenciaIntento i) { bloqueos++; return leer(i); }
        @Override public void crear(ReferenciaIntento i, UUID idGuion) {
            escrituras.add("crear " + idGuion);
            IntentoConFase f = filas.get(i.valor());
            filas.put(i.valor(), new IntentoConFase(f.estado(), f.limiteTemporal(), f.ahora(),
                    Optional.of(new Fase(idGuion, FaseAcademica.EXPLORACION, ahora, Optional.empty()))));
        }
        /** Como el UPDATE real: {@code ahora} es el instante de la escritura, que decide el límite y se graba. */
        @Override public Optional<Instant> marcarCuestionario(ReferenciaIntento i) {
            IntentoConFase f = filas.get(i.valor());
            if (ahora.isAfter(f.limiteTemporal())) {
                return Optional.empty();
            }
            escrituras.add("cuestionario");
            Fase previa = f.fase().orElseThrow();
            filas.put(i.valor(), new IntentoConFase(f.estado(), f.limiteTemporal(), f.ahora(),
                    Optional.of(new Fase(previa.idGuion(), FaseAcademica.CUESTIONARIO, previa.fechaInicioExploracion(), Optional.of(ahora)))));
            return Optional.of(ahora);
        }
    }

    final Fases fases = new Fases();
    Optional<UUID> guionDelCaso = Optional.empty();
    final Map<UUID, GuionesDelIntento.VersionGuion> versiones = new HashMap<>();
    final GuionesDelIntento guiones = new GuionesDelIntento() {
        @Override public Optional<UUID> publicadoParaConvocatoria(ReferenciaConvocatoria c) { return guionDelCaso; }
        @Override public Optional<VersionGuion> version(UUID id) { return Optional.ofNullable(versiones.get(id)); }
    };
    final FasesIntento servicio = new FasesIntento(fases, guiones, new PoliticasFaseAprobadas());

    void intento(EstadoIntento estado, Duration limiteDesdeAhora, FaseAcademica fase) {
        Optional<RepositorioFases.Fase> f = switch (fase) {
            case SIN_FASES -> Optional.empty();
            case EXPLORACION -> Optional.of(new RepositorioFases.Fase(guion, fase, T0.minusSeconds(60), Optional.empty()));
            case CUESTIONARIO -> Optional.of(new RepositorioFases.Fase(guion, fase, T0.minusSeconds(60), Optional.of(T0.minusSeconds(30))));
        };
        fases.filas.put(intento.valor(), new RepositorioFases.IntentoConFase(estado, T0.plus(limiteDesdeAhora), T0, f));
    }

    ResultadoInicio inicio(boolean reanudado) {
        return new ResultadoInicio(new ResumenIntento(intento, EstadoIntento.EN_CURSO, Optional.empty(), List.of()),
                T0, T0.plusSeconds(1800), reanudado);
    }

    // --- Creación en el inicio (C1, R5) -------------------------------------------------------------------------

    @Test @DisplayName("un intento nuevo de un caso con guion publicado nace en EXPLORACION con esa versión")
    void inicioConGuionCreaLaFase() {
        intento(EstadoIntento.EN_CURSO, Duration.ofMinutes(30), FaseAcademica.SIN_FASES);
        guionDelCaso = Optional.of(guion);
        servicio.alIniciar(new ReferenciaConvocatoria(UUID.randomUUID().toString()), inicio(false));
        assertEquals(List.of("crear " + guion), fases.escrituras);
    }

    @Test @DisplayName("sin guion publicado o al reanudar no se crea ninguna fase (C1/C2)")
    void sinGuionOReanudacionNoCreaNada() {
        intento(EstadoIntento.EN_CURSO, Duration.ofMinutes(30), FaseAcademica.SIN_FASES);
        servicio.alIniciar(new ReferenciaConvocatoria(UUID.randomUUID().toString()), inicio(false));
        guionDelCaso = Optional.of(guion);
        servicio.alIniciar(new ReferenciaConvocatoria(UUID.randomUUID().toString()), inicio(true));
        assertEquals(List.of(), fases.escrituras);
    }

    // --- R2 ----------------------------------------------------------------------------------------------------

    @Test @DisplayName("R2: en EXPLORACION no se admiten respuestas; la comprobación bloquea la fila del intento")
    void respuestasRechazadasEnExploracion() {
        intento(EstadoIntento.EN_CURSO, Duration.ofMinutes(30), FaseAcademica.EXPLORACION);
        assertThrows(CuestionarioNoIniciadoException.class, () -> servicio.exigirRespuestasAdmitidas(intento));
        assertEquals(1, fases.bloqueos);
        assertEquals(List.of(), fases.escrituras);
    }

    @Test @DisplayName("R2 solo afecta a intentos con fases: CUESTIONARIO, SIN_FASES e inexistente pasan sin cambios")
    void respuestasAdmitidasFueraDeExploracion() {
        intento(EstadoIntento.EN_CURSO, Duration.ofMinutes(30), FaseAcademica.CUESTIONARIO);
        servicio.exigirRespuestasAdmitidas(intento);
        intento(EstadoIntento.EN_CURSO, Duration.ofMinutes(30), FaseAcademica.SIN_FASES);
        servicio.exigirRespuestasAdmitidas(intento);
        servicio.exigirRespuestasAdmitidas(new ReferenciaIntento(UUID.randomUUID().toString()));
        assertEquals(List.of(), fases.escrituras);
    }

    // --- P2: consulta y recuperación -------------------------------------------------------------------------------

    @Test @DisplayName("P2 devuelve la fase persistida, sus fechas, el límite y la hora del servidor")
    void consultarFase() {
        intento(EstadoIntento.EN_CURSO, Duration.ofMinutes(30), FaseAcademica.EXPLORACION);
        EstadoFase e = servicio.consultar(intento);
        assertEquals(FaseAcademica.EXPLORACION, e.fase());
        assertEquals(Optional.of(T0.minusSeconds(60)), e.fechaInicioExploracion());
        assertEquals(Optional.empty(), e.fechaInicioCuestionario());
        assertEquals(T0.plusSeconds(1800), e.limiteTemporal());
        assertEquals(T0, e.ahoraServidor());
        intento(EstadoIntento.FINALIZADO, Duration.ofMinutes(30), FaseAcademica.SIN_FASES);
        EstadoFase sin = servicio.consultar(intento);
        assertEquals(FaseAcademica.SIN_FASES, sin.fase());
        assertEquals(EstadoIntento.FINALIZADO, sin.estadoIntento());
        assertThrows(IntentoNoEncontradoException.class, () -> servicio.consultar(new ReferenciaIntento(UUID.randomUUID().toString())));
    }

    // --- P3: transición ----------------------------------------------------------------------------------------

    @Test @DisplayName("P3 marca el cuestionario una vez; repetirlo devuelve la misma fecha sin escribir (R3)")
    void cuestionarioIdempotente() {
        intento(EstadoIntento.EN_CURSO, Duration.ofMinutes(30), FaseAcademica.EXPLORACION);
        EntradaCuestionario primera = servicio.iniciarCuestionario(intento);
        assertFalse(primera.yaIniciado());
        assertEquals(T0, primera.fechaInicioCuestionario());
        fases.ahora = T0.plusSeconds(5);
        EntradaCuestionario repetida = servicio.iniciarCuestionario(intento);
        assertTrue(repetida.yaIniciado());
        assertEquals(primera.fechaInicioCuestionario(), repetida.fechaInicioCuestionario());
        assertEquals(List.of("cuestionario"), fases.escrituras);
    }

    @Test @DisplayName("D-SIM-3 (QA33-03): lectura bloqueada antes del límite y escritura después → LIMITE_TEMPORAL_VENCIDO, sin escribir")
    void limiteVencidoEntreLecturaYEscritura() {
        intento(EstadoIntento.EN_CURSO, Duration.ofSeconds(1), FaseAcademica.EXPLORACION); // leída en T0, límite T0+1 s
        fases.ahora = T0.plusSeconds(2); // instante de la escritura, ya vencido
        var vencido = assertThrows(LimiteTemporalVencidoException.class, () -> servicio.iniciarCuestionario(intento));
        assertEquals(PoliticasFaseAprobadas.MENSAJE_LIMITE, vencido.getMessage());
        assertEquals(List.of(), fases.escrituras);
        assertEquals(FaseAcademica.EXPLORACION, servicio.consultar(intento).fase());
        fases.ahora = T0.plusSeconds(1); // exactamente en el límite: se admite y se graba ese instante
        assertEquals(T0.plusSeconds(1), servicio.iniciarCuestionario(intento).fechaInicioCuestionario());
    }

    @Test @DisplayName("P3 rechaza el intento finalizado y el intento sin fases, sin escribir")
    void cuestionarioRechazos() {
        intento(EstadoIntento.FINALIZADO, Duration.ofMinutes(30), FaseAcademica.EXPLORACION);
        assertThrows(IntentoFinalizadoException.class, () -> servicio.iniciarCuestionario(intento));
        intento(EstadoIntento.EN_CURSO, Duration.ofMinutes(30), FaseAcademica.SIN_FASES);
        assertThrows(SinFasesException.class, () -> servicio.iniciarCuestionario(intento));
        assertEquals(List.of(), fases.escrituras);
    }

    @Test @DisplayName("D-SIM-3: tras el límite la entrada se rechaza (LIMITE_TEMPORAL_VENCIDO) sin escribir; ya iniciado sigue siendo idempotente")
    void cuestionarioTrasElLimiteRechazado() {
        intento(EstadoIntento.EN_CURSO, Duration.ofSeconds(-1), FaseAcademica.EXPLORACION);
        var vencido = assertThrows(LimiteTemporalVencidoException.class, () -> servicio.iniciarCuestionario(intento));
        assertEquals(PoliticasFaseAprobadas.MENSAJE_LIMITE, vencido.getMessage());
        assertEquals(List.of(), fases.escrituras);
        assertEquals(1, fases.bloqueos, "el límite se compara con la fila del intento bloqueada");
        // Exactamente en el límite todavía se admite (mismo criterio que las respuestas: vencido solo si ahora > límite).
        intento(EstadoIntento.EN_CURSO, Duration.ZERO, FaseAcademica.EXPLORACION);
        assertFalse(servicio.iniciarCuestionario(intento).yaIniciado());
        fases.escrituras.clear();
        intento(EstadoIntento.EN_CURSO, Duration.ofSeconds(-1), FaseAcademica.CUESTIONARIO);
        assertTrue(servicio.iniciarCuestionario(intento).yaIniciado());
        assertEquals(List.of(), fases.escrituras);
    }

    @Test @DisplayName("cada decisión se consulta solo en su situación exacta y en un único punto")
    void cadaDecisionEnSuPunto() {
        List<String> consultados = new ArrayList<>();
        var registro = new PuntosDecisionFase() {
            @Override public void preguntasSinCuestionario(ReferenciaIntento i, EstadoIntento e) { consultados.add("D-SIM-2 " + e); }
            @Override public void entradaAlCuestionarioTrasElLimite(ReferenciaIntento i) { consultados.add("D-SIM-3"); }
            @Override public void finalizacionDesdeExploracion(ReferenciaIntento i) { consultados.add("D-SIM-4"); }
            @Override public void narrativaDeIntentoFinalizado(ReferenciaIntento i) { consultados.add("D-SIM-10"); }
        };
        var otro = new FasesIntento(fases, guiones, registro);
        versiones.put(guion, new GuionesDelIntento.VersionGuion(guion, 1, "c".repeat(64), "{}"));
        intento(EstadoIntento.EN_CURSO, Duration.ofMinutes(30), FaseAcademica.EXPLORACION);
        otro.exigirPreguntasVisibles(intento);
        otro.alFinalizar(intento);
        otro.narrativa(intento);
        intento(EstadoIntento.EN_CURSO, Duration.ofSeconds(-1), FaseAcademica.EXPLORACION);
        // QA33-03: el límite lo decide la escritura; una política que no rechazara no puede persistir la entrada tardía.
        assertThrows(IllegalStateException.class, () -> otro.iniciarCuestionario(intento));
        intento(EstadoIntento.FINALIZADO, Duration.ofMinutes(30), FaseAcademica.EXPLORACION);
        otro.exigirPreguntasVisibles(intento);
        otro.alFinalizar(intento);
        otro.narrativa(intento);
        intento(EstadoIntento.EN_CURSO, Duration.ofMinutes(30), FaseAcademica.CUESTIONARIO);
        otro.exigirPreguntasVisibles(intento);
        otro.alFinalizar(intento);
        intento(EstadoIntento.EN_CURSO, Duration.ofMinutes(30), FaseAcademica.SIN_FASES);
        otro.exigirPreguntasVisibles(intento);
        otro.alFinalizar(intento);
        assertEquals(List.of("D-SIM-2 EN_CURSO", "D-SIM-4", "D-SIM-3", "D-SIM-2 FINALIZADO", "D-SIM-10"), consultados);
    }

    // --- D-SIM-2: preguntas ocultas ---------------------------------------------------------------------------

    @Test @DisplayName("D-SIM-2: preguntas ocultas en exploración y tras finalizar sin cuestionario; visibles en cuestionario y SIN_FASES")
    void preguntasOcultasSinCuestionario() {
        intento(EstadoIntento.EN_CURSO, Duration.ofMinutes(30), FaseAcademica.EXPLORACION);
        assertThrows(CuestionarioNoIniciadoException.class, () -> servicio.exigirPreguntasVisibles(intento));
        intento(EstadoIntento.FINALIZADO, Duration.ofMinutes(30), FaseAcademica.EXPLORACION);
        assertThrows(CuestionarioNoIniciadoException.class, () -> servicio.exigirPreguntasVisibles(intento));
        for (EstadoIntento estado : EstadoIntento.values()) {
            intento(estado, Duration.ofMinutes(30), FaseAcademica.CUESTIONARIO);
            servicio.exigirPreguntasVisibles(intento);
            intento(estado, Duration.ofMinutes(30), FaseAcademica.SIN_FASES);
            servicio.exigirPreguntasVisibles(intento);
        }
        servicio.exigirPreguntasVisibles(new ReferenciaIntento(UUID.randomUUID().toString()));
        assertEquals(0, fases.bloqueos, "es una lectura en la instantánea, sin bloqueo");
        assertEquals(List.of(), fases.escrituras);
    }

    // --- D-SIM-4: finalización desde exploración ----------------------------------------------------------------

    @Test @DisplayName("D-SIM-4: finalizar desde exploración está permitido y no escribe ni cambia la fase")
    void finalizacionDesdeExploracionPermitida() {
        for (FaseAcademica fase : FaseAcademica.values()) {
            for (EstadoIntento estado : EstadoIntento.values()) {
                intento(estado, Duration.ofMinutes(30), fase);
                servicio.alFinalizar(intento);
                assertEquals(fase, servicio.consultar(intento).fase());
            }
        }
        intento(EstadoIntento.EN_CURSO, Duration.ofSeconds(-1), FaseAcademica.EXPLORACION);
        servicio.alFinalizar(intento); // vencido: no se cierra ni se rechaza por tiempo aquí
        servicio.alFinalizar(new ReferenciaIntento(UUID.randomUUID().toString()));
        assertEquals(List.of(), fases.escrituras);
    }

    // --- P1: narrativa -----------------------------------------------------------------------------------------

    @Test @DisplayName("P1 sirve la versión fijada en EXPLORACION y cierra la exploración en CUESTIONARIO (R4)")
    void narrativa() {
        versiones.put(guion, new GuionesDelIntento.VersionGuion(guion, 2, "a".repeat(64), "{\"formato\":\"prisaly-guion-1\"}"));
        intento(EstadoIntento.EN_CURSO, Duration.ofMinutes(30), FaseAcademica.EXPLORACION);
        NarrativaIntento n = servicio.narrativa(intento);
        assertEquals(guion, n.idGuion());
        assertEquals(2, n.version());
        assertEquals(idIntento, n.idIntento());
        intento(EstadoIntento.EN_CURSO, Duration.ofMinutes(30), FaseAcademica.CUESTIONARIO);
        assertThrows(ExploracionCerradaException.class, () -> servicio.narrativa(intento));
    }

    @Test @DisplayName("P1: sin fases o sin guion disponible, NARRATIVA_NO_DISPONIBLE; D-SIM-10: finalizado, EXPLORACION_CERRADA")
    void narrativaNoDisponibleYFinalizado() {
        intento(EstadoIntento.EN_CURSO, Duration.ofMinutes(30), FaseAcademica.SIN_FASES);
        assertThrows(NarrativaNoDisponibleException.class, () -> servicio.narrativa(intento));
        intento(EstadoIntento.EN_CURSO, Duration.ofMinutes(30), FaseAcademica.EXPLORACION);
        assertThrows(NarrativaNoDisponibleException.class, () -> servicio.narrativa(intento));
        versiones.put(guion, new GuionesDelIntento.VersionGuion(guion, 1, "b".repeat(64), "{}"));
        for (FaseAcademica fase : List.of(FaseAcademica.EXPLORACION, FaseAcademica.CUESTIONARIO)) {
            intento(EstadoIntento.FINALIZADO, Duration.ofMinutes(30), fase);
            assertThrows(ExploracionCerradaException.class, () -> servicio.narrativa(intento));
            assertEquals(fase, servicio.consultar(intento).fase(), "no reabre ni modifica la fase");
        }
        assertEquals(List.of(), fases.escrituras);
    }

    // --- P4: completitud ---------------------------------------------------------------------------------------

    @Test @DisplayName("P4 cuenta las preguntas presentables en el orden del caso e ignora respuestas ajenas al contenido")
    void completitudInformativa() {
        UUID p1 = UUID.randomUUID(), p2 = UUID.randomUUID(), p3 = UUID.randomUUID(), ajena = UUID.randomUUID();
        var e2 = new ContenidoCasoEstudiantilPort.Escenario(UUID.randomUUID(), "E2", null, null, 2,
                List.of(new ContenidoCasoEstudiantilPort.Pregunta(p3, "P3", 1, List.of())));
        var e1 = new ContenidoCasoEstudiantilPort.Escenario(UUID.randomUUID(), "E1", null, null, 1,
                List.of(new ContenidoCasoEstudiantilPort.Pregunta(p2, "P2", 2, List.of()),
                        new ContenidoCasoEstudiantilPort.Pregunta(p1, "P1", 1, List.of())));
        var contenido = new ContenidoIntento(idIntento, UUID.randomUUID(),
                new ContenidoCasoEstudiantilPort.Caso(UUID.randomUUID(), "Caso", null, null, List.of(e2, e1)));
        var resumen = new ResumenIntento(intento, EstadoIntento.EN_CURSO, Optional.empty(), List.of(
                new Respuesta(new ReferenciaPregunta(p2.toString().toUpperCase()), new ValorRespuesta(UUID.randomUUID().toString())),
                new Respuesta(new ReferenciaPregunta(ajena.toString()), new ValorRespuesta(UUID.randomUUID().toString()))));
        CompletitudIntento c = CompletitudIntento.de(contenido, resumen);
        assertEquals(3, c.preguntasPresentables());
        assertEquals(1, c.respondidas());
        assertEquals(List.of(p1, p3), c.sinResponder());
    }
}
