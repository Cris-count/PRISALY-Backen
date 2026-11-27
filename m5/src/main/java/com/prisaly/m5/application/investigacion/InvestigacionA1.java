package com.prisaly.m5.application.investigacion;

import static com.prisaly.m5.application.investigacion.InvestigacionA1RechazadaException.Codigo.INTENTO_CERRADO;

import com.prisaly.m5.application.ReferenciaIntento;
import com.prisaly.m5.application.fase.EstadoFase;
import com.prisaly.m5.application.fase.FaseAcademica;
import com.prisaly.m5.application.fase.NarrativaIntento;
import com.prisaly.m5.domain.intento.EstadoIntento;

import java.time.Clock;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;

/**
 * CB-02B (A1): servicio de investigación del intento sobre el modelo de eventos. <b>Sin Spring, sin endpoints y sin
 * esquema</b>: no se registra como bean y no activa nada. Reutiliza lo que M5 ya tiene en lugar de duplicarlo:
 * <ul>
 * <li>titularidad e identidad estudiantil: {@code AccesoProtegidoParticipacion.consultarIntento} (vía {@link Titularidad});</li>
 * <li>estado, fase y guion fijado: {@link com.prisaly.m5.application.fase.FasesIntento#consultar} y {@code #narrativa}
 *     (vía {@link FaseYGuion}).</li>
 * </ul>
 * La persistencia ({@link Repositorio}) queda como puerto: su tabla es una migración candidata pendiente de D-ESQ-1.
 *
 * <p>Orden de cada escritura: titularidad → intento en curso (y en exploración, para eventos) → guion fijado → reglas
 * puras → escritura. Un rechazo nunca escribe.
 */
public final class InvestigacionA1 {

    /** Lanza la excepción de acceso de M5 si el intento no es del estudiante autenticado. */
    @FunctionalInterface
    public interface Titularidad {
        void exigir(ReferenciaIntento intento);
    }

    public interface FaseYGuion {
        EstadoFase estado(ReferenciaIntento intento);

        NarrativaIntento narrativa(ReferenciaIntento intento);
    }

    /** Puerto de persistencia. {@code eventosBloqueados} se lee con el intento bloqueado (serializa la secuencia). */
    public interface Repositorio {
        List<RegistroEventosA1.Evento> eventosBloqueados(UUID idIntento);

        List<RegistroEventosA1.Evento> eventos(UUID idIntento, long desdeSecuencia);

        void insertar(UUID idIntento, RegistroEventosA1.Evento evento);

        Optional<RegistroNotasA1.Nota> nota(UUID idIntento, UUID idNota);

        List<RegistroNotasA1.Nota> notas(UUID idIntento);

        void guardar(UUID idIntento, RegistroNotasA1.Nota nota);
    }

    public record Progreso(UUID idIntento, UUID idGuion, String hashDefinicion, EstadoIntento estadoIntento, FaseAcademica fase,
            List<RegistroEventosA1.Evento> eventos, List<RegistroNotasA1.Nota> notas) { }

    private final Titularidad titularidad;
    private final FaseYGuion fases;
    private final Repositorio repositorio;
    private final Function<String, Map<String, Object>> lectorJson;
    private final Clock reloj;

    public InvestigacionA1(Titularidad titularidad, FaseYGuion fases, Repositorio repositorio,
            Function<String, Map<String, Object>> lectorJson, Clock reloj) {
        this.titularidad = Objects.requireNonNull(titularidad, "titularidad");
        this.fases = Objects.requireNonNull(fases, "fases");
        this.repositorio = Objects.requireNonNull(repositorio, "repositorio");
        this.lectorJson = Objects.requireNonNull(lectorJson, "lectorJson");
        this.reloj = Objects.requireNonNull(reloj, "reloj");
    }

    public RegistroEventosA1.Resultado registrarEvento(UUID idIntento, RegistroEventosA1.Solicitud solicitud) {
        ReferenciaIntento ref = referencia(idIntento);
        titularidad.exigir(ref);
        EstadoFase estado = fases.estado(ref);
        if (estado.estadoIntento() != EstadoIntento.EN_CURSO || estado.fase() != FaseAcademica.EXPLORACION) {
            throw new InvestigacionA1RechazadaException(INTENTO_CERRADO, estado.estadoIntento() + "/" + estado.fase());
        }
        GuionFijado guion = guionFijado(ref);
        var resultado = RegistroEventosA1.registrar(guion, repositorio.eventosBloqueados(idIntento), solicitud, reloj.instant());
        if (resultado.nuevo()) {
            repositorio.insertar(idIntento, resultado.evento());
        }
        return resultado;
    }

    public RegistroNotasA1.Nota guardarNota(UUID idIntento, UUID idNota, String texto, long revisionEsperada) {
        ReferenciaIntento ref = referencia(idIntento);
        exigirEscrituraDeNotas(ref);
        var nota = RegistroNotasA1.guardar(repositorio.nota(idIntento, idNota), idNota, texto, revisionEsperada, reloj.instant());
        repositorio.guardar(idIntento, nota);
        return nota;
    }

    public void eliminarNota(UUID idIntento, UUID idNota, long revisionEsperada) {
        ReferenciaIntento ref = referencia(idIntento);
        exigirEscrituraDeNotas(ref);
        RegistroNotasA1.eliminar(repositorio.nota(idIntento, idNota), idNota, revisionEsperada, reloj.instant())
                .ifPresent(n -> repositorio.guardar(idIntento, n));
    }

    /** Recuperación tras recarga (A1 «Flujo de recarga»): eventos desde una secuencia y notas no eliminadas. */
    public Progreso progreso(UUID idIntento, long desdeSecuencia) {
        ReferenciaIntento ref = referencia(idIntento);
        titularidad.exigir(ref);
        EstadoFase estado = fases.estado(ref);
        NarrativaIntento narrativa = fases.narrativa(ref);
        return new Progreso(idIntento, narrativa.idGuion(), narrativa.hashDefinicion(), estado.estadoIntento(), estado.fase(),
                repositorio.eventos(idIntento, desdeSecuencia),
                repositorio.notas(idIntento).stream().filter(n -> !n.eliminada()).toList());
    }

    private void exigirEscrituraDeNotas(ReferenciaIntento ref) {
        titularidad.exigir(ref);
        EstadoFase estado = fases.estado(ref);
        if (estado.estadoIntento() != EstadoIntento.EN_CURSO) {
            throw new InvestigacionA1RechazadaException(INTENTO_CERRADO, String.valueOf(estado.estadoIntento()));
        }
    }

    private GuionFijado guionFijado(ReferenciaIntento ref) {
        NarrativaIntento n = fases.narrativa(ref);
        return GuionFijado.desde(n.idGuion(), n.hashDefinicion(), lectorJson.apply(n.definicionJson()));
    }

    private static ReferenciaIntento referencia(UUID idIntento) {
        return new ReferenciaIntento(Objects.requireNonNull(idIntento, "idIntento").toString());
    }
}
