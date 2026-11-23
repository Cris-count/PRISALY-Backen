package com.prisaly.config.m6;

import com.prisaly.m3.application.port.AutorizacionContextoAcademicoPort;
import com.prisaly.m6.application.AccesoEvaluacionDenegadoException;
import com.prisaly.m6.application.port.SolicitudCapacidadContextoAcademicoPort;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Internal M3–M6 capability for {@code CONSULTAR_CONTEXTO_EVALUABLE} (switch-guarded assembly only).
 *
 * <ul>
 *   <li><b>Issue</b> (M6 side): only to the current active teacher who owns the case; otherwise
 *       {@link AccesoEvaluacionDenegadoException}. 32 random bytes, bound to the case and to that teacher.</li>
 *   <li><b>Verify</b> (M3 side): single use (removed on the first verification), expires after 60 s, same case,
 *       same teacher, exact operation. Anything else is DENEGADO.</li>
 * </ul>
 * In-memory and bounded; tokens are never logged or put in exception messages. Not a Spring bean: an issuer and a
 * verifier only exist inside the M6 evaluation facade, so no other component can obtain or check capabilities.
 */
final class CapacidadesContextoEvaluacionM6
        implements SolicitudCapacidadContextoAcademicoPort, AutorizacionContextoAcademicoPort {
    static final Duration VIGENCIA = Duration.ofSeconds(60);
    static final int MAXIMO_PENDIENTES = 256;

    private record Emitida(UUID idCaso, UUID docente, Instant vence) { }

    private final PropietarioActivoCasoM6 propietario;
    private final Clock reloj;
    private final SecureRandom aleatorio = new SecureRandom();
    private final Map<String, Emitida> pendientes = new ConcurrentHashMap<>();

    CapacidadesContextoEvaluacionM6(PropietarioActivoCasoM6 propietario, Clock reloj) {
        this.propietario = Objects.requireNonNull(propietario, "propietario");
        this.reloj = Objects.requireNonNull(reloj, "reloj");
    }

    @Override
    public String solicitarCapacidad(UUID idIntento, UUID idCaso) {
        if (idIntento == null || idCaso == null || !propietario.esDocenteActivoPropietario(idCaso)) {
            throw new AccesoEvaluacionDenegadoException();
        }
        UUID docente = propietario.docenteActivo().orElseThrow(AccesoEvaluacionDenegadoException::new);
        Instant ahora = reloj.instant();
        pendientes.values().removeIf(e -> !ahora.isBefore(e.vence()));
        if (pendientes.size() >= MAXIMO_PENDIENTES) {
            throw new IllegalStateException("Capacidades de evaluacion agotadas temporalmente");
        }
        byte[] bytes = new byte[32];
        aleatorio.nextBytes(bytes);
        String capacidad = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        pendientes.put(capacidad, new Emitida(idCaso, docente, ahora.plus(VIGENCIA)));
        return capacidad;
    }

    @Override
    public Decision verificar(String capacidad, UUID idCaso, Operacion operacion) {
        if (capacidad == null || idCaso == null || operacion != Operacion.CONSULTAR_CONTEXTO_EVALUABLE) {
            return Decision.DENEGADO;
        }
        Emitida emitida = pendientes.remove(capacidad); // single use, also when it then fails
        if (emitida == null || !reloj.instant().isBefore(emitida.vence()) || !idCaso.equals(emitida.idCaso())) {
            return Decision.DENEGADO;
        }
        return propietario.docenteActivo().map(emitida.docente()::equals).orElse(false)
                ? Decision.AUTORIZADO : Decision.DENEGADO;
    }

    int pendientes() {
        return pendientes.size();
    }
}
