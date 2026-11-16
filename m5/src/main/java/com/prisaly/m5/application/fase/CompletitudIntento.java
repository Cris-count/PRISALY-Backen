package com.prisaly.m5.application.fase;

import com.prisaly.m3.api.ContenidoCasoEstudiantilPort;
import com.prisaly.m5.application.ContenidoIntento;
import com.prisaly.m5.application.ResumenIntento;

import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Completitud <b>informativa</b> del intento (contrato P4, candidato): cuántas preguntas presentables hay y cuáles
 * siguen sin respuesta. No impone ningún criterio de aprobación ni bloquea nada (D-SIM-5 sin ratificar), y no revela
 * aciertos: solo identificadores de pregunta.
 *
 * <p>Las preguntas presentables son exactamente las del contenido autorizado de M3 para el intento, en el orden del
 * caso (escenario y pregunta). Una respuesta a una pregunta que no figura en ese contenido no cuenta.
 */
public record CompletitudIntento(UUID idIntento, int preguntasPresentables, int respondidas, List<UUID> sinResponder) {
    public CompletitudIntento {
        Objects.requireNonNull(idIntento, "idIntento");
        sinResponder = List.copyOf(sinResponder);
        if (preguntasPresentables < 0 || respondidas < 0 || respondidas + sinResponder.size() != preguntasPresentables) {
            throw new IllegalArgumentException("Recuento de completitud incoherente");
        }
    }

    public static CompletitudIntento de(ContenidoIntento contenido, ResumenIntento intento) {
        Objects.requireNonNull(contenido, "contenido");
        Objects.requireNonNull(intento, "intento");
        List<UUID> presentables = contenido.caso().escenarios().stream()
                .sorted(Comparator.comparingInt(ContenidoCasoEstudiantilPort.Escenario::orden))
                .flatMap(e -> e.preguntas().stream()
                        .sorted(Comparator.comparingInt(ContenidoCasoEstudiantilPort.Pregunta::orden)))
                .map(ContenidoCasoEstudiantilPort.Pregunta::idPregunta)
                .toList();
        Set<String> respondidas = intento.respuestas().stream()
                .map(r -> r.pregunta().valor().toLowerCase(Locale.ROOT))
                .collect(Collectors.toSet());
        List<UUID> sinResponder = presentables.stream().filter(p -> !respondidas.contains(p.toString())).toList();
        return new CompletitudIntento(contenido.idIntento(), presentables.size(),
                presentables.size() - sinResponder.size(), sinResponder);
    }
}
