package com.prisaly.m5.infrastructure.evaluacion;

import com.prisaly.m4.api.ProgramacionParticipacionPort;
import com.prisaly.m5.application.IntentoNoEncontradoException;
import com.prisaly.m5.application.ReferenciaIntento;
import com.prisaly.m5.application.acceso.AccesoProtegidoParticipacion;
import com.prisaly.m5.application.acceso.AccesoIntentoDenegadoException;
import com.prisaly.m5.application.acceso.EstudianteNoIdentificadoException;
import com.prisaly.m5.application.acceso.IdentidadEstudianteActual;
import com.prisaly.m5.application.port.RepositorioIntentos;
import com.prisaly.m6.application.AccesoEvaluacionDenegadoException;
import com.prisaly.m6.application.integration.IntentoEvaluacion;
import com.prisaly.m6.application.integration.IntentoEvaluacion.RespuestaReferencia;
import com.prisaly.m6.application.port.AutorizacionEvaluacionPort;
import com.prisaly.m6.application.port.ConsultaIntentoEvaluacionPort;
import com.prisaly.m6.domain.strategy.SolicitudEvaluacion;
import java.util.HashSet;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

/** Request-bound reader: institutional authorization AND M5 protection on every invocation.
 * No global bean, cached authorization, student enablement, or academic permission inference.
 */
public final class ConsultaIntentoEvaluacionDesdeM5 implements ConsultaIntentoEvaluacionPort {
    private final SolicitudEvaluacion solicitud;
    private final AutorizacionEvaluacionPort autorizacion;
    private final AccesoProtegidoParticipacion protegida;
    private final RepositorioIntentos repositorio;
    private final IdentidadEstudianteActual identidad;
    private final ProgramacionParticipacionPort programacion;
    private final TransactionTemplate transaccion;

    ConsultaIntentoEvaluacionDesdeM5(SolicitudEvaluacion solicitud,
            AutorizacionEvaluacionPort autorizacion, AccesoProtegidoParticipacion protegida,
            RepositorioIntentos repositorio, IdentidadEstudianteActual identidad,
            ProgramacionParticipacionPort programacion, PlatformTransactionManager transacciones) {
        this.solicitud = Objects.requireNonNull(solicitud);
        this.autorizacion = Objects.requireNonNull(autorizacion);
        this.protegida = Objects.requireNonNull(protegida);
        this.repositorio = Objects.requireNonNull(repositorio);
        this.identidad = Objects.requireNonNull(identidad);
        this.programacion = Objects.requireNonNull(programacion);
        transaccion = new TransactionTemplate(Objects.requireNonNull(transacciones));
        // A fresh stable snapshot also when the caller already has a weaker transaction.
        transaccion.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        transaccion.setIsolationLevel(TransactionDefinition.ISOLATION_REPEATABLE_READ);
        transaccion.setReadOnly(true);
    }

    @Override
    public Optional<IntentoEvaluacion> findById(UUID idIntento) {
        Objects.requireNonNull(idIntento, "idIntento");
        if (!solicitud.idIntento().equals(idIntento)) { throw new AccesoEvaluacionDenegadoException(); }
        try {
            if (autorizacion.verificarAcceso(solicitud)
                    != AutorizacionEvaluacionPort.DecisionAcceso.AUTORIZADO) {
                throw new AccesoEvaluacionDenegadoException();
            }
        } catch (RuntimeException failure) {
            throw new AccesoEvaluacionDenegadoException();
        }
        return transaccion.execute(status -> consultar(idIntento));
    }

    private Optional<IntentoEvaluacion> consultar(UUID id) {
        var referencia = new ReferenciaIntento(id.toString());
        final com.prisaly.m5.application.ResumenIntento resumen;
        try {
            resumen = protegida.consultarIntento(referencia);
        } catch (IntentoNoEncontradoException absent) {
            return Optional.empty();
        } catch (AccesoIntentoDenegadoException | EstudianteNoIdentificadoException denied) {
            throw new AccesoEvaluacionDenegadoException();
        }
        if (!referencia.equals(resumen.referencia())) { throw inconsistente(); }
        var estudiante = identidad.actual().orElseThrow(AccesoEvaluacionDenegadoException::new);
        var vinculo = repositorio.vinculo(referencia).orElseThrow(ConsultaIntentoEvaluacionDesdeM5::inconsistente);
        var contexto = Objects.requireNonNull(programacion.consultarConvocatoria(
                vinculo.idConvocatoria(), UUID.fromString(estudiante.valor())))
                .orElseThrow(AccesoEvaluacionDenegadoException::new);
        if (!vinculo.idConvocatoria().equals(contexto.convocatoriaId())
                || !UUID.fromString(estudiante.valor()).equals(contexto.estudianteId())) {
            throw inconsistente();
        }
        var preguntas = new HashSet<UUID>();
        var respuestas = resumen.respuestas().stream().map(r -> {
            UUID pregunta = UUID.fromString(r.pregunta().valor());
            if (!preguntas.add(pregunta)) { throw inconsistente(); }
            return new RespuestaReferencia(pregunta, UUID.fromString(r.valor().valor()));
        }).toList();
        // EN_CURSO is preserved: the existing M6 preparation rejects it explicitly.
        return Optional.of(new IntentoEvaluacion(id, vinculo.idParticipacion(), contexto.casoId(),
                IntentoEvaluacion.EstadoIntento.valueOf(resumen.estado().name()), respuestas));
    }

    private static IllegalStateException inconsistente() {
        return new IllegalStateException("Relaciones persistidas del intento inconsistentes");
    }
}
