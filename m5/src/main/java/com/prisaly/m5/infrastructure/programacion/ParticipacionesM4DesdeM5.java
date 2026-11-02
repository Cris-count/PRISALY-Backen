package com.prisaly.m5.infrastructure.programacion;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

import org.springframework.stereotype.Component;

import com.prisaly.m4.application.port.ConvocatoriaRepository;
import com.prisaly.m4.application.port.ParticipacionesPort;
import com.prisaly.m4.domain.model.Convocatoria;
import com.prisaly.m5.api.HistorialParticipacionPort;

/**
 * Proveedor de {@link ParticipacionesPort} (puerto que M4 declara para saber si
 * una sesión tiene historial) sobre el contrato de M5 {@link HistorialParticipacionPort}.
 *
 * <p>Resuelve el ciclo de dependencias de M5-INC-08 con el mismo patrón que M7
 * usa para {@code NotificacionPort}: el módulo proveedor implementa el puerto de
 * M4 ({@code m5 → m4}, sin {@code m4 → m5}). Al existir este bean, el respaldo de
 * M4 que respondía 503 ({@code @ConditionalOnMissingBean}) deja de registrarse.
 *
 * <ul>
 *   <li>Las convocatorias de la sesión se piden a M4 por su puerto
 *       {@link ConvocatoriaRepository#listarPorSesion}, que M4 documenta como
 *       usado por M5. M5 no lee la tabla {@code "Convocatoria"}.</li>
 *   <li>La existencia de participaciones se resuelve en la tabla de M5
 *       {@code "Participacion"}. Un Intento exige una Participacion (FK), así que
 *       cubre "participaciones o intentos".</li>
 *   <li>Se ejecuta en la transacción de M4, que ya bloqueó la fila de la sesión
 *       ({@code buscarParaActualizar}, {@code FOR UPDATE}). El historial exige esa
 *       transacción ({@code Propagation.MANDATORY}).</li>
 *   <li>Un error técnico se propaga como excepción: nunca se convierte en
 *       {@code false} ("sin historial").</li>
 * </ul>
 */
@Component
public class ParticipacionesM4DesdeM5 implements ParticipacionesPort {

    private final ConvocatoriaRepository convocatorias;
    private final HistorialParticipacionPort historial;

    public ParticipacionesM4DesdeM5(ConvocatoriaRepository convocatorias, HistorialParticipacionPort historial) {
        this.convocatorias = Objects.requireNonNull(convocatorias, "convocatorias");
        this.historial = Objects.requireNonNull(historial, "historial");
    }

    @Override
    public boolean tieneParticipacionesOIntentos(UUID sesionId) {
        if (sesionId == null) {
            throw new IllegalArgumentException("La sesión es obligatoria");
        }
        List<Convocatoria> deLaSesion = convocatorias.listarPorSesion(sesionId);
        if (deLaSesion == null || deLaSesion.stream().anyMatch(c -> c == null || c.getId() == null)) {
            throw new IllegalStateException("M4 devolvió convocatorias incompletas para la sesión");
        }
        return historial.existeParticipacion(deLaSesion.stream().map(Convocatoria::getId).toList());
    }
}
