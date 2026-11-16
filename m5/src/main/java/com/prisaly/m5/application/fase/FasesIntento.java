package com.prisaly.m5.application.fase;

import com.prisaly.m5.application.DatoAplicacionInvalidoException;
import com.prisaly.m5.application.IntentoNoEncontradoException;
import com.prisaly.m5.application.PersistenciaIntentoException;
import com.prisaly.m5.application.ReferenciaConvocatoria;
import com.prisaly.m5.application.ReferenciaIntento;
import com.prisaly.m5.application.ResultadoInicio;
import com.prisaly.m5.application.port.GuionesDelIntento;
import com.prisaly.m5.application.port.RepositorioFases;
import com.prisaly.m5.application.port.RepositorioFases.IntentoConFase;
import com.prisaly.m5.domain.intento.EstadoIntento;
import com.prisaly.m5.domain.intento.IntentoFinalizadoException;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

/**
 * Lógica de las fases académicas del intento (Incremento B: B2a en DC-BE-SIM-B1-B2A-24, B2b en DC-BE-SIM-B2B-B3-32).
 * Reglas estructurales: R2 (sin respuestas en exploración), R3 (entrada explícita e idempotente), R4 (sin exploración
 * durante el cuestionario) y R5 (fase persistida en el servidor). Las decisiones académicas D-SIM-2, D-SIM-3, D-SIM-4 y
 * D-SIM-10 se resuelven cada una en su método de {@link PuntosDecisionFase}, al que esta clase llama solo en la
 * situación exacta de esa decisión.
 *
 * <p>Invariante C1/C2: un intento sin fila {@code "FaseIntento"} es {@link FaseAcademica#SIN_FASES} y nada de lo que
 * hace esta clase cambia su comportamiento. Sin Spring: las transacciones las abre el ensamblado de M5.
 */
public final class FasesIntento {

    private final RepositorioFases fases;
    private final GuionesDelIntento guiones;
    private final PuntosDecisionFase decisiones;

    public FasesIntento(RepositorioFases fases, GuionesDelIntento guiones, PuntosDecisionFase decisiones) {
        if (fases == null || guiones == null || decisiones == null) {
            throw new DatoAplicacionInvalidoException("Las fases requieren repositorio, guiones y puntos de decisión");
        }
        this.fases = fases;
        this.guiones = guiones;
        this.decisiones = decisiones;
    }

    /**
     * En la transacción del inicio, después de crear el intento: si el caso tiene un guion publicado, el intento nace
     * en {@code EXPLORACION} con esa versión fijada. La reanudación no crea fases (el intento ya existía) y un caso sin
     * guion no crea nada. Si la inserción falla, la transacción del inicio se revierte entera.
     */
    public void alIniciar(ReferenciaConvocatoria convocatoria, ResultadoInicio resultado) {
        if (resultado == null || resultado.reanudado()) {
            return;
        }
        Optional<UUID> guion = guiones.publicadoParaConvocatoria(convocatoria);
        guion.ifPresent(id -> fases.crear(resultado.intento().referencia(), id));
    }

    /**
     * R2: en un intento con fases que sigue en {@code EXPLORACION}, ninguna respuesta académica se registra ni se
     * modifica. Se evalúa con la fila {@code "Intento"} bloqueada, de modo que no hay carrera con la entrada al
     * cuestionario. Un intento {@code SIN_FASES} o inexistente pasa sin cambios (lo trata el servicio de respuestas).
     */
    public void exigirRespuestasAdmitidas(ReferenciaIntento intento) {
        Optional<IntentoConFase> fila = fases.bloquear(requerir(intento));
        if (fila.isPresent() && fila.get().fase().map(f -> f.fase() == FaseAcademica.EXPLORACION).orElse(false)) {
            throw new CuestionarioNoIniciadoException();
        }
    }

    /**
     * D-SIM-2: las preguntas de un intento con fases solo se leen después de entrar al cuestionario. Se evalúa en la
     * misma instantánea que el contenido o la completitud que se va a entregar, después de la titularidad. Un intento
     * {@code SIN_FASES} o inexistente pasa sin cambios (lo trata la consulta de contenido).
     */
    public void exigirPreguntasVisibles(ReferenciaIntento intento) {
        Optional<IntentoConFase> fila = fases.leer(requerir(intento));
        if (fila.isPresent() && enExploracion(fila.get())) {
            decisiones.preguntasSinCuestionario(intento, fila.get().estado());
        }
    }

    /**
     * D-SIM-4: antes de finalizar, en la misma transacción. Un intento en curso que sigue en {@code EXPLORACION} pasa por
     * su punto de decisión; la finalización en sí (titularidad, idempotencia y motivo) es la de M5, sin cambios. La fase
     * no se modifica: el intento queda finalizado en la fase en la que estaba. No hay cierre automático por vencimiento.
     */
    public void alFinalizar(ReferenciaIntento intento) {
        Optional<IntentoConFase> fila = fases.leer(requerir(intento));
        if (fila.isPresent() && fila.get().estado() == EstadoIntento.EN_CURSO && enExploracion(fila.get())) {
            decisiones.finalizacionDesdeExploracion(intento);
        }
    }

    private static boolean enExploracion(IntentoConFase fila) {
        return fila.fase().map(f -> f.fase() == FaseAcademica.EXPLORACION).orElse(false);
    }

    /** Fase persistida (P2). La recuperación tras recarga o un nuevo inicio de sesión se basa solo en esta lectura. */
    public EstadoFase consultar(ReferenciaIntento intento) {
        IntentoConFase fila = fases.leer(requerir(intento)).orElseThrow(() -> new IntentoNoEncontradoException(intento));
        UUID id = UUID.fromString(intento.valor());
        if (fila.fase().isEmpty()) {
            return new EstadoFase(id, fila.estado(), FaseAcademica.SIN_FASES, Optional.empty(), Optional.empty(),
                    fila.limiteTemporal(), fila.ahora());
        }
        var fase = fila.fase().get();
        return new EstadoFase(id, fila.estado(), fase.fase(), Optional.of(fase.fechaInicioExploracion()),
                fase.fechaInicioCuestionario(), fila.limiteTemporal(), fila.ahora());
    }

    /**
     * Entrada al cuestionario (P3), atómica e idempotente, con la fila {@code "Intento"} bloqueada. Orden:
     * finalizado → {@link IntentoFinalizadoException}; sin fases → {@link SinFasesException}; ya en cuestionario →
     * la fecha persistida con {@code yaIniciado=true} (no escribe ni depende de ninguna política, así que un reintento
     * nunca revierte una entrada ya confirmada aunque el límite haya vencido después); en otro caso, una única sentencia
     * marca el cuestionario solo si su propio instante de PostgreSQL no supera el límite, y si lo supera → D-SIM-3.
     */
    public EntradaCuestionario iniciarCuestionario(ReferenciaIntento intento) {
        IntentoConFase fila = fases.bloquear(requerir(intento)).orElseThrow(() -> new IntentoNoEncontradoException(intento));
        UUID id = UUID.fromString(intento.valor());
        if (fila.estado() != EstadoIntento.EN_CURSO) {
            throw new IntentoFinalizadoException("iniciar el cuestionario");
        }
        var fase = fila.fase().orElseThrow(SinFasesException::new);
        if (fase.fase() == FaseAcademica.CUESTIONARIO) {
            return new EntradaCuestionario(id, fase.fechaInicioCuestionario().orElseThrow(), true);
        }
        // D-SIM-3 (QA33-03): el límite se decide en la propia escritura, con el mismo instante de PostgreSQL que se graba.
        // Una lectura previa del reloj no sirve de autoridad: el límite puede vencer entre ella y el UPDATE.
        Optional<Instant> fecha = fases.marcarCuestionario(intento);
        if (fecha.isEmpty()) {
            decisiones.entradaAlCuestionarioTrasElLimite(intento); // LIMITE_TEMPORAL_VENCIDO; la fase sigue en exploración
            throw new IllegalStateException("D-SIM-3 debe rechazar la entrada al cuestionario después del límite");
        }
        return new EntradaCuestionario(id, fecha.get(), false);
    }

    /**
     * Narrativa del intento (P1): la versión fijada en su fase. En {@code EXPLORACION} con el intento en curso se sirve;
     * en {@code CUESTIONARIO} con el intento en curso, {@link ExploracionCerradaException} (R4); con el intento
     * finalizado, D-SIM-10 (cerrada, sin reabrir ni modificar la fase); sin fases, {@link NarrativaNoDisponibleException}.
     */
    public NarrativaIntento narrativa(ReferenciaIntento intento) {
        IntentoConFase fila = fases.leer(requerir(intento)).orElseThrow(() -> new IntentoNoEncontradoException(intento));
        var fase = fila.fase().orElseThrow(NarrativaNoDisponibleException::new);
        if (fila.estado() != EstadoIntento.EN_CURSO) {
            decisiones.narrativaDeIntentoFinalizado(intento);
        } else if (fase.fase() == FaseAcademica.CUESTIONARIO) {
            throw new ExploracionCerradaException();
        }
        var version = guiones.version(fase.idGuion()).orElseThrow(NarrativaNoDisponibleException::new);
        if (!version.idGuion().equals(fase.idGuion())) {
            throw new PersistenciaIntentoException("Guion servido distinto del fijado en la fase");
        }
        return new NarrativaIntento(UUID.fromString(intento.valor()), version.idGuion(), version.version(),
                version.hashDefinicion(), version.definicionJson());
    }

    private static ReferenciaIntento requerir(ReferenciaIntento intento) {
        if (intento == null) {
            throw new DatoAplicacionInvalidoException("La referencia del intento es obligatoria");
        }
        return intento;
    }
}
