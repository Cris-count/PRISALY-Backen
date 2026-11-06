package com.prisaly.m5.application.servicio;

import java.time.Instant;
import java.util.Optional;

import com.prisaly.m5.application.DatoAplicacionInvalidoException;
import com.prisaly.m5.application.IntentoNoEncontradoException;
import com.prisaly.m5.application.ReferenciaConvocatoria;
import com.prisaly.m5.application.ReferenciaIntento;
import com.prisaly.m5.application.RepeticionNoHabilitadaException;
import com.prisaly.m5.application.ResultadoInicio;
import com.prisaly.m5.application.ResumenIntento;
import com.prisaly.m5.application.inicio.DecisionPendiente;
import com.prisaly.m5.application.inicio.ElegibilidadInicio;
import com.prisaly.m5.application.inicio.InicioNoAutorizadoException;
import com.prisaly.m5.application.inicio.InicioNoDisponibleException;
import com.prisaly.m5.application.inicio.LimiteTemporalInicio;
import com.prisaly.m5.application.port.RepositorioIntentos;
import com.prisaly.m5.application.port.RepositorioParticipaciones;
import com.prisaly.m5.application.port.RepositorioParticipaciones.IntentoEnCurso;
import com.prisaly.m5.application.port.RepositorioParticipaciones.ReferenciaParticipacion;

/**
 * Caso de uso: iniciar o reanudar el intento de una convocatoria.
 *
 * <p>Se ejecuta en una sola transacción, después de verificar identidad (M1) y
 * titularidad de la convocatoria (M4).
 *
 * <ol>
 *   <li>Bloquea la participación de la convocatoria, si existe.</li>
 *   <li><b>Reanudación:</b> si hay un intento {@code EnCurso}, lo devuelve tal
 *       como está (mismo id, respuestas, inicio y límite). No consulta
 *       elegibilidad ni límite, y no escribe.</li>
 *   <li>Si la participación ya tiene un intento finalizado, iniciar sería una
 *       repetición: se rechaza (DD-14 pendiente).</li>
 *   <li><b>Primer inicio:</b> elegibilidad y límite temporal; si alguna decisión
 *       falta, falla sin escribir. Después crea o bloquea la participación,
 *       vuelve a comprobar el paso 2 (otra solicitud pudo crearla mientras
 *       tanto) e inserta el intento 1.</li>
 * </ol>
 *
 * <p>No duplica las reglas de la base: unicidad de participación e intento en
 * curso, tiempos y repetición entre sesiones (V001–V007) siguen en PostgreSQL.
 */
public final class IniciarIntentoServicio {

    private final RepositorioParticipaciones participaciones;
    private final RepositorioIntentos intentos;
    private final ElegibilidadInicio elegibilidad;
    private final LimiteTemporalInicio limiteTemporal;

    public IniciarIntentoServicio(
            RepositorioParticipaciones participaciones,
            RepositorioIntentos intentos,
            ElegibilidadInicio elegibilidad,
            LimiteTemporalInicio limiteTemporal) {
        if (participaciones == null || intentos == null || elegibilidad == null || limiteTemporal == null) {
            throw new DatoAplicacionInvalidoException("El inicio requiere repositorios, elegibilidad y límite temporal");
        }
        this.participaciones = participaciones;
        this.intentos = intentos;
        this.elegibilidad = elegibilidad;
        this.limiteTemporal = limiteTemporal;
    }

    public ResultadoInicio iniciarOReanudar(ReferenciaConvocatoria convocatoria) {
        if (convocatoria == null) {
            throw new DatoAplicacionInvalidoException("La referencia de la convocatoria es obligatoria");
        }
        Optional<ReferenciaParticipacion> existente = participaciones.bloquear(convocatoria);
        if (existente.isPresent()) {
            Optional<ResultadoInicio> reanudado = reanudar(existente.get());
            if (reanudado.isPresent()) {
                return reanudado.get();
            }
        }

        exigirElegibilidad(convocatoria);
        Instant inicio = participaciones.ahora();
        Optional<Instant> calculado = limiteTemporal.calcular(convocatoria, inicio);
        if (calculado == null || calculado.isEmpty()) {
            throw new InicioNoDisponibleException(DecisionPendiente.LIMITE_TEMPORAL);
        }
        Instant limite = calculado.get();

        ReferenciaParticipacion participacion;
        if (existente.isPresent()) {
            participacion = existente.get();
        } else {
            participacion = participaciones.crearOBloquear(convocatoria);
            Optional<ResultadoInicio> reanudado = reanudar(participacion);
            if (reanudado.isPresent()) {
                return reanudado.get();
            }
        }
        ReferenciaIntento nuevo = participaciones.crearPrimerIntento(participacion, inicio, limite);
        return new ResultadoInicio(resumen(nuevo), inicio, limite, false);
    }

    /** Intento en curso de la participación bloqueada; vacío si no tiene ninguno. */
    private Optional<ResultadoInicio> reanudar(ReferenciaParticipacion participacion) {
        Optional<IntentoEnCurso> enCurso = participaciones.intentoEnCurso(participacion);
        if (enCurso.isPresent()) {
            IntentoEnCurso intento = enCurso.get();
            return Optional.of(new ResultadoInicio(
                    resumen(intento.referencia()), intento.inicio(), intento.limiteTemporal(), true));
        }
        if (participaciones.tieneIntentos(participacion)) {
            throw new RepeticionNoHabilitadaException();
        }
        return Optional.empty();
    }

    private void exigirElegibilidad(ReferenciaConvocatoria convocatoria) {
        ElegibilidadInicio.Resultado resultado = elegibilidad.evaluar(convocatoria);
        if (resultado == ElegibilidadInicio.Resultado.ELEGIBLE) {
            return;
        }
        if (resultado == ElegibilidadInicio.Resultado.NO_ELEGIBLE) {
            throw new InicioNoAutorizadoException();
        }
        // REGLA_NO_RATIFICADA o null: no se puede determinar, no se inicia.
        throw new InicioNoDisponibleException(DecisionPendiente.ELEGIBILIDAD_ACADEMICA);
    }

    private ResumenIntento resumen(ReferenciaIntento referencia) {
        return intentos.buscar(referencia)
                .map(intento -> intentos.resumir(referencia, intento))
                .orElseThrow(() -> new IntentoNoEncontradoException(referencia));
    }
}
