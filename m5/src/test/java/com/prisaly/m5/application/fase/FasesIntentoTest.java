package com.prisaly.m5.application.fase;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.prisaly.m3.api.ContenidoCasoEstudiantilPort;
import com.prisaly.m5.application.ContenidoIntento;
import com.prisaly.m5.application.IntentoNoEncontradoException;
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
        @Override public Instant marcarCuestionario(ReferenciaIntento i) {
            escrituras.add("cuestionario");
            IntentoConFase f = filas.get(i.valor());
            Fase previa = f.fase().orElseThrow();
            filas.put(i.valor(), new IntentoConFase(f.estado(), f.limiteTemporal(), f.ahora(),
                    Optional.of(new Fase(previa.idGuion(), FaseAcademica.CUESTIONARIO, previa.fechaInicioExploracion(), Optional.of(ahora)))));
            return ahora;
        }
    }

    final Fases fases = new Fases();
    Optional<UUID> guionDelCaso = Optional.empty();
    final Map<UUID, GuionesDelIntento.VersionGuion> versiones = new HashMap<>();
    final GuionesDelIntento guiones = new GuionesDelIntento() {
        @Override public Optional<UUID> publicadoParaConvocatoria(ReferenciaConvocatoria c) { return guionDelCaso; }
        @Override public Optional<VersionGuion> version(UUID id) { return Optional.ofNullable(versiones.get(id)); }
    };
    final FasesIntento servicio = new FasesIntento(fases, guiones, PuntosDecisionFase.noRatificados());

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

    @Test @DisplayName("P3 rechaza el intento finalizado y el intento sin fases, sin escribir")
    void cuestionarioRechazos() {
        intento(EstadoIntento.FINALIZADO, Duration.ofMinutes(30), FaseAcademica.EXPLORACION);
        assertThrows(IntentoFinalizadoException.class, () -> servicio.iniciarCuestionario(intento));
        intento(EstadoIntento.EN_CURSO, Duration.ofMinutes(30), FaseAcademica.SIN_FASES);
        assertThrows(SinFasesException.class, () -> servicio.iniciarCuestionario(intento));
        assertEquals(List.of(), fases.escrituras);
    }

    @Test @DisplayName("P3 tras el límite: D-SIM-3 no ratificada falla cerrado y no escribe; ya iniciado sigue siendo idempotente")
    void cuestionarioTrasElLimiteDependeDeDSim3() {
        intento(EstadoIntento.EN_CURSO, Duration.ofSeconds(-1), FaseAcademica.EXPLORACION);
        var pendiente = assertThrows(DecisionFaseNoRatificadaException.class, () -> servicio.iniciarCuestionario(intento));
        assertEquals(DecisionFasePendiente.D_SIM_3, pendiente.decision());
        assertEquals(List.of(), fases.escrituras);
        intento(EstadoIntento.EN_CURSO, Duration.ofSeconds(-1), FaseAcademica.CUESTIONARIO);
        assertTrue(servicio.iniciarCuestionario(intento).yaIniciado());
        assertEquals(List.of(), fases.escrituras);
    }

    @Test @DisplayName("una decisión ratificada en el futuro se aplica sustituyendo solo su punto de decisión")
    void puntoDeDecisionSustituible() {
        List<String> consultados = new ArrayList<>();
        var permitirTodo = new PuntosDecisionFase() {
            @Override public void entradaAlCuestionarioTrasElLimite(ReferenciaIntento i) { consultados.add("D-SIM-3"); }
            @Override public void narrativaDeIntentoFinalizado(ReferenciaIntento i) { consultados.add("D-SIM-10"); }
        };
        var otro = new FasesIntento(fases, guiones, permitirTodo);
        intento(EstadoIntento.EN_CURSO, Duration.ofSeconds(-1), FaseAcademica.EXPLORACION);
        assertFalse(otro.iniciarCuestionario(intento).yaIniciado());
        assertEquals(List.of("D-SIM-3"), consultados);
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

    @Test @DisplayName("P1: sin fases o sin guion disponible, NARRATIVA_NO_DISPONIBLE; intento finalizado, D-SIM-10 pendiente")
    void narrativaNoDisponibleYFinalizado() {
        intento(EstadoIntento.EN_CURSO, Duration.ofMinutes(30), FaseAcademica.SIN_FASES);
        assertThrows(NarrativaNoDisponibleException.class, () -> servicio.narrativa(intento));
        intento(EstadoIntento.EN_CURSO, Duration.ofMinutes(30), FaseAcademica.EXPLORACION);
        assertThrows(NarrativaNoDisponibleException.class, () -> servicio.narrativa(intento));
        versiones.put(guion, new GuionesDelIntento.VersionGuion(guion, 1, "b".repeat(64), "{}"));
        for (FaseAcademica fase : List.of(FaseAcademica.EXPLORACION, FaseAcademica.CUESTIONARIO)) {
            intento(EstadoIntento.FINALIZADO, Duration.ofMinutes(30), fase);
            var pendiente = assertThrows(DecisionFaseNoRatificadaException.class, () -> servicio.narrativa(intento));
            assertEquals(DecisionFasePendiente.D_SIM_10, pendiente.decision());
        }
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
