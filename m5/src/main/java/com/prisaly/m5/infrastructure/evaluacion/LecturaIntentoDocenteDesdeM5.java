package com.prisaly.m5.infrastructure.evaluacion;

import com.prisaly.m4.api.ResponsableConvocatoriaPort;
import com.prisaly.m5.application.ReferenciaIntento;
import com.prisaly.m5.application.port.RepositorioIntentos;
import com.prisaly.m5.domain.intento.EstadoIntento;
import com.prisaly.m6.application.AccesoEvaluacionDenegadoException;
import com.prisaly.m6.application.integration.IntentoEvaluacion;
import com.prisaly.m6.application.integration.IntentoEvaluacion.RespuestaReferencia;
import com.prisaly.m6.application.port.ConsultaIntentoEvaluacionPort;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * B1 (DC-BE-M6-DESBLOQUEO-IMPLEMENTACION-55): internal M5→M6 <b>teacher</b> reading port for the evaluation of one
 * attempt. It is not a generic reader and has no route:
 *
 * <ul>
 *   <li>Identifies the participation and the call ({@link RepositorioIntentos#vinculo}) and the programmed case
 *       through M4's {@link ResponsableConvocatoriaPort}, without reading other modules' tables.</li>
 *   <li>Requires that the current teacher is <b>active and the owner of that case</b> ({@link PropietarioDelCaso},
 *       supplied by APP over M1 identity and M3 authorship). Otherwise {@link AccesoEvaluacionDenegadoException}.
 *       The student identity is never used or impersonated.</li>
 *   <li>Returns the answers <b>only of finalized attempts</b>. A non-finalized attempt is reported as
 *       {@code EN_CURSO} without answers, so M6 rejects it ({@code INTENTO_NO_FINALIZADO}).</li>
 *   <li>Everything is read in one read-only {@code REPEATABLE READ} transaction of its own ({@code REQUIRES_NEW}),
 *       so link, case, authorship and answers come from the same snapshot.</li>
 * </ul>
 * It writes nothing and changes no M5 rule (E1, L1, RF-22, V1-c, DD-14-a). Not a Spring bean: only the
 * switch-guarded APP assembly creates it.
 */
public final class LecturaIntentoDocenteDesdeM5 implements ConsultaIntentoEvaluacionPort {

    /** APP-supplied check: the current authenticated teacher is active and created this case. */
    @FunctionalInterface
    public interface PropietarioDelCaso {
        /** @throws RuntimeException technical failure, never a silent {@code true} */
        boolean esDocenteActivoPropietario(UUID idCaso);
    }

    private final RepositorioIntentos repositorio;
    private final ResponsableConvocatoriaPort responsables;
    private final PropietarioDelCaso propietario;
    private final TransactionTemplate transaccion;

    public LecturaIntentoDocenteDesdeM5(RepositorioIntentos repositorio, ResponsableConvocatoriaPort responsables,
            PropietarioDelCaso propietario, PlatformTransactionManager transacciones) {
        this.repositorio = Objects.requireNonNull(repositorio, "repositorio");
        this.responsables = Objects.requireNonNull(responsables, "responsables");
        this.propietario = Objects.requireNonNull(propietario, "propietario");
        transaccion = new TransactionTemplate(Objects.requireNonNull(transacciones, "transacciones"));
        transaccion.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        transaccion.setIsolationLevel(TransactionDefinition.ISOLATION_REPEATABLE_READ);
        transaccion.setReadOnly(true);
    }

    /** @return empty if the attempt does not exist; denied if it is not of a case of the current teacher */
    @Override
    public Optional<IntentoEvaluacion> findById(UUID idIntento) {
        Objects.requireNonNull(idIntento, "idIntento");
        return transaccion.execute(status -> leer(idIntento));
    }

    /**
     * Ownership only (used by the APP authorization and by the teacher grade query); reveals no attempt data.
     * @return false if the attempt does not exist or its case is not of the current active teacher
     */
    public boolean perteneceAlDocenteActual(UUID idIntento) {
        Objects.requireNonNull(idIntento, "idIntento");
        return Boolean.TRUE.equals(transaccion.execute(status -> {
            var referencia = new ReferenciaIntento(idIntento.toString());
            return repositorio.vinculo(referencia)
                    .map(v -> propietario.esDocenteActivoPropietario(caso(v.idConvocatoria())))
                    .orElse(false);
        }));
    }

    private Optional<IntentoEvaluacion> leer(UUID id) {
        var referencia = new ReferenciaIntento(id.toString());
        var vinculo = repositorio.vinculo(referencia);
        if (vinculo.isEmpty()) {
            return Optional.empty();
        }
        UUID idCaso = caso(vinculo.get().idConvocatoria());
        if (!propietario.esDocenteActivoPropietario(idCaso)) {
            throw new AccesoEvaluacionDenegadoException();
        }
        var intento = repositorio.buscar(referencia).orElseThrow(LecturaIntentoDocenteDesdeM5::inconsistente);
        if (intento.estado() != EstadoIntento.FINALIZADO) {
            return Optional.of(new IntentoEvaluacion(id, vinculo.get().idParticipacion(), idCaso,
                    IntentoEvaluacion.EstadoIntento.EN_CURSO, List.of()));
        }
        var preguntas = new HashSet<UUID>();
        var respuestas = intento.respuestas().stream().map(r -> {
            UUID pregunta = UUID.fromString(r.pregunta().valor());
            if (!preguntas.add(pregunta)) { throw inconsistente(); }
            return new RespuestaReferencia(pregunta, UUID.fromString(r.valor().valor()));
        }).toList();
        return Optional.of(new IntentoEvaluacion(id, vinculo.get().idParticipacion(), idCaso,
                IntentoEvaluacion.EstadoIntento.FINALIZADO, respuestas));
    }

    private UUID caso(UUID idConvocatoria) {
        return Objects.requireNonNull(responsables.consultar(idConvocatoria))
                .orElseThrow(LecturaIntentoDocenteDesdeM5::inconsistente).idCaso();
    }

    private static IllegalStateException inconsistente() {
        return new IllegalStateException("Relaciones persistidas del intento inconsistentes");
    }
}
