package com.prisaly.m5.infrastructure.persistencia;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import com.prisaly.m5.application.ReferenciaIntento;
import com.prisaly.m5.domain.intento.EstadoIntento;
import com.prisaly.m5.domain.intento.Intento;
import com.prisaly.m5.domain.intento.MotivoFinalizacion;
import com.prisaly.m5.domain.respuesta.ReferenciaPregunta;
import com.prisaly.m5.domain.respuesta.Respuesta;
import com.prisaly.m5.domain.respuesta.ValorRespuesta;

/**
 * Traducción entre el agregado {@link Intento} y las filas de BD-005.2.
 *
 * <p>Correspondencias (V001–V009):
 * <ul>
 *   <li>{@link ReferenciaIntento} ↔ {@code "Intento"."idIntento"} (uuid).</li>
 *   <li>{@link EstadoIntento} ↔ {@code "Intento"."estado"}: EN_CURSO ↔
 *       {@code 'EnCurso'}, FINALIZADO ↔ {@code 'Finalizado'} (catálogo de V007).</li>
 *   <li>{@link MotivoFinalizacion} ↔ {@code "Intento"."motivoFinalizacion"}
 *       (varchar 40): se guarda el nombre de la constante. V007 no define un
 *       catálogo, de modo que el valor definitivo sigue pendiente.</li>
 *   <li>{@link ReferenciaPregunta} ↔ {@code "Respuesta"."idPregunta"} (uuid) y
 *       {@link ValorRespuesta} ↔ {@code "Respuesta"."idOpcionSeleccionada"}
 *       (uuid de {@code "OpcionRespuesta"}).</li>
 * </ul>
 * Sin dependencias de JPA: se prueba de forma unitaria.
 */
public final class MapeoIntento {

    static final String EN_CURSO = "EnCurso";
    static final String FINALIZADO = "Finalizado";

    /** Fila de {@code "Intento"} con las columnas que M5 interpreta. */
    public record FilaIntento(String estado, String motivoFinalizacion) {
    }

    /** Fila de {@code "Respuesta"} con las columnas que M5 interpreta. */
    public record FilaRespuesta(UUID idPregunta, UUID idOpcionSeleccionada) {
    }

    /** Cambio que el adaptador debe aplicar a {@code "Respuesta"}. */
    public record CambioRespuesta(UUID idPregunta, UUID idOpcionSeleccionada, boolean nueva) {
    }

    private MapeoIntento() {
    }

    public static String estadoAColumna(EstadoIntento estado) {
        return switch (estado) {
            case EN_CURSO -> EN_CURSO;
            case FINALIZADO -> FINALIZADO;
        };
    }

    /** Uuid de la referencia, o vacío si no tiene formato uuid (no puede existir en la base). */
    public static Optional<UUID> idDe(ReferenciaIntento referencia) {
        try {
            return Optional.of(UUID.fromString(referencia.valor()));
        } catch (IllegalArgumentException e) {
            return Optional.empty();
        }
    }

    /**
     * Reconstruye el agregado reproduciendo sus operaciones de dominio, sin
     * agregar constructores de persistencia al dominio. Las respuestas se
     * reproducen en el orden recibido.
     *
     * @throws PersistenciaIntentoException si las filas violan las reglas del dominio
     */
    public static Intento reconstruir(FilaIntento fila, List<FilaRespuesta> respuestas) {
        Intento intento = Intento.iniciar();
        for (FilaRespuesta respuesta : respuestas) {
            intento.registrarRespuesta(
                    new ReferenciaPregunta(respuesta.idPregunta().toString()),
                    new ValorRespuesta(respuesta.idOpcionSeleccionada().toString()));
        }
        if (FINALIZADO.equals(fila.estado())) {
            intento.finalizar(motivoDe(fila.motivoFinalizacion()));
        } else if (EN_CURSO.equals(fila.estado())) {
            if (fila.motivoFinalizacion() != null) {
                throw new PersistenciaIntentoException("Intento EnCurso con motivo de finalización registrado");
            }
        } else {
            throw new PersistenciaIntentoException("Estado de intento desconocido: " + fila.estado());
        }
        return intento;
    }

    /**
     * Calcula las inserciones y reemplazos necesarios para que
     * {@code "Respuesta"} refleje el agregado. El dominio nunca elimina
     * respuestas: si la base tiene una que el agregado no conoce, el estado es
     * inconsistente y no se guarda nada.
     *
     * @param existentes opción seleccionada persistida por cada pregunta
     */
    public static List<CambioRespuesta> cambios(Map<UUID, UUID> existentes, Intento intento) {
        List<CambioRespuesta> cambios = new ArrayList<>();
        Set<UUID> preguntasDelAgregado = new HashSet<>();
        for (Respuesta respuesta : intento.respuestas()) {
            UUID pregunta = uuid(respuesta.pregunta().valor(), "La referencia de la pregunta");
            UUID opcion = uuid(respuesta.valor().valor(), "El valor de la respuesta");
            preguntasDelAgregado.add(pregunta);
            UUID persistida = existentes.get(pregunta);
            if (persistida == null) {
                cambios.add(new CambioRespuesta(pregunta, opcion, true));
            } else if (!persistida.equals(opcion)) {
                cambios.add(new CambioRespuesta(pregunta, opcion, false));
            }
        }
        if (!preguntasDelAgregado.containsAll(existentes.keySet())) {
            throw new PersistenciaIntentoException("La base contiene respuestas que el intento no conoce");
        }
        return cambios;
    }

    private static MotivoFinalizacion motivoDe(String columna) {
        if (columna == null) {
            throw new PersistenciaIntentoException("Intento Finalizado sin motivo de finalización");
        }
        try {
            return MotivoFinalizacion.valueOf(columna);
        } catch (IllegalArgumentException e) {
            throw new PersistenciaIntentoException("Motivo de finalización desconocido: " + columna, e);
        }
    }

    private static UUID uuid(String valor, String dato) {
        try {
            return UUID.fromString(valor);
        } catch (IllegalArgumentException e) {
            throw new PersistenciaIntentoException(dato + " debe ser un identificador uuid de BD-005.2: " + valor, e);
        }
    }
}
