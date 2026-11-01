package com.prisaly.m5.infrastructure.persistencia;

import java.time.OffsetDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import com.prisaly.m5.application.ReferenciaIntento;
import com.prisaly.m5.application.port.RepositorioIntentos;
import com.prisaly.m5.domain.intento.Intento;
import com.prisaly.m5.infrastructure.persistencia.MapeoIntento.CambioRespuesta;
import com.prisaly.m5.infrastructure.persistencia.MapeoIntento.FilaIntento;
import com.prisaly.m5.infrastructure.persistencia.MapeoIntento.FilaRespuesta;

/**
 * Adaptador JPA de {@link RepositorioIntentos} sobre BD-005.2.
 *
 * <p>Ambas operaciones exigen una transacción ya abierta
 * ({@link Propagation#MANDATORY}): cargar, mutar y guardar el agregado deben
 * pertenecer a la misma unidad de trabajo, que abre
 * {@link com.prisaly.m5.infrastructure.FachadaParticipacionTransaccional}.
 *
 * <p>Concurrencia: en una transacción de escritura, {@link #buscar} bloquea
 * la fila de {@code "Intento"} ({@code SELECT ... FOR UPDATE}) hasta el fin
 * de la transacción. Así, dos casos de uso sobre el mismo intento (por
 * ejemplo, registrar una respuesta y finalizar por tiempo) se serializan, y
 * el segundo ve el resultado confirmado del primero. Se eligió bloqueo
 * pesimista porque {@code "Intento"} no tiene columna de versión para un
 * control optimista. En transacciones de solo lectura no se bloquea.
 *
 * <p>Reloj: las marcas {@code "Intento"."fin"} y
 * {@code "Respuesta"."fechaActualizacion"} se toman de PostgreSQL
 * ({@code clock_timestamp()}), igual que los valores por defecto ({@code now()})
 * y los triggers de BD-005.2. Así comparten reloj con {@code "inicio"} y
 * {@code ck_intento_tiempos} ({@code "fin" >= "inicio"}) no depende de la
 * diferencia entre el reloj de la JVM y el del servidor de base de datos.
 *
 * <p>No crea intentos: insertar en {@code "Intento"} corresponde al caso de
 * uso de inicio, bloqueado por M1, M4 y DD-14.
 */
@Repository
public class RepositorioIntentosJpa implements RepositorioIntentos {

    private final IntentoJpaRepository intentos;
    private final RespuestaJpaRepository respuestas;
    private final JdbcTemplate jdbc;

    public RepositorioIntentosJpa(
            IntentoJpaRepository intentos, RespuestaJpaRepository respuestas, JdbcTemplate jdbc) {
        this.intentos = intentos;
        this.respuestas = respuestas;
        this.jdbc = jdbc;
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public Optional<Intento> buscar(ReferenciaIntento referencia) {
        Optional<UUID> id = MapeoIntento.idDe(referencia);
        if (id.isEmpty()) {
            return Optional.empty();
        }
        Optional<IntentoEntity> fila = TransactionSynchronizationManager.isCurrentTransactionReadOnly()
                ? intentos.findById(id.get())
                : intentos.bloquearPorId(id.get());
        return fila.map(entidad -> MapeoIntento.reconstruir(
                new FilaIntento(entidad.getEstado(), entidad.getMotivoFinalizacion()),
                filasRespuesta(id.get())));
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public void guardar(ReferenciaIntento referencia, Intento intento) {
        UUID id = MapeoIntento.idDe(referencia).orElseThrow(
                () -> new PersistenciaIntentoException("La referencia no es un idIntento uuid: " + referencia.valor()));
        IntentoEntity entidad = intentos.bloquearPorId(id).orElseThrow(() -> new PersistenciaIntentoException(
                "El intento " + id + " no existe: crear intentos corresponde al caso de uso de inicio"));
        // Después del bloqueo de la fila: la marca es posterior a cualquier
        // escritura confirmada sobre el intento.
        OffsetDateTime ahora = ahoraEnBaseDeDatos();

        Map<UUID, RespuestaEntity> existentes = new HashMap<>();
        for (RespuestaEntity respuesta : respuestas.findByIdIntentoOrderByIdPreguntaAsc(id)) {
            existentes.put(respuesta.getIdPregunta(), respuesta);
        }
        Map<UUID, UUID> opcionesPersistidas = new HashMap<>();
        existentes.forEach((pregunta, respuesta) -> opcionesPersistidas.put(pregunta, respuesta.getIdOpcionSeleccionada()));

        for (CambioRespuesta cambio : MapeoIntento.cambios(opcionesPersistidas, intento)) {
            if (cambio.nueva()) {
                respuestas.save(RespuestaEntity.nueva(id, cambio.idPregunta(), cambio.idOpcionSeleccionada(), ahora));
            } else {
                existentes.get(cambio.idPregunta()).reemplazarOpcion(cambio.idOpcionSeleccionada(), ahora);
            }
        }
        entidad.reflejar(
                MapeoIntento.estadoAColumna(intento.estado()),
                intento.motivoFinalizacion().map(Enum::name).orElse(null),
                ahora);
        // Hace visibles en esta transacción las violaciones de restricciones y triggers.
        respuestas.flush();
    }

    /** Hora del servidor PostgreSQL, en la conexión de la transacción en curso. */
    private OffsetDateTime ahoraEnBaseDeDatos() {
        OffsetDateTime ahora = jdbc.queryForObject("SELECT clock_timestamp()", OffsetDateTime.class);
        if (ahora == null) {
            throw new PersistenciaIntentoException("PostgreSQL no devolvió la hora del servidor");
        }
        return ahora;
    }

    private List<FilaRespuesta> filasRespuesta(UUID idIntento) {
        return respuestas.findByIdIntentoOrderByIdPreguntaAsc(idIntento).stream()
                .map(r -> new FilaRespuesta(r.getIdPregunta(), r.getIdOpcionSeleccionada()))
                .toList();
    }
}
