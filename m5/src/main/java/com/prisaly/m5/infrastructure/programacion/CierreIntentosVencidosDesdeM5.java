package com.prisaly.m5.infrastructure.programacion;

import com.prisaly.m4.api.ConvocatoriasSesionPort;
import com.prisaly.m4.api.ReglaNegocioException;
import com.prisaly.m4.application.port.CierreParticipacionesPort;
import com.prisaly.m5.application.ReferenciaIntento;

import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Predicate;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * Proveedor de {@link CierreParticipacionesPort} (V1-c, DC-BE-M5-CIERRE-IMPLEMENTACION-45): cuando el docente cierra una
 * sesión, cierra con {@code TIEMPO_VENCIDO} los intentos de esa sesión cuyo límite ya pasó. Mismo patrón que
 * {@link ParticipacionesM4DesdeM5}.
 *
 * <ul>
 *   <li>Las convocatorias de la sesión se piden a M4 por su contrato publicado ({@link ConvocatoriasSesionPort}); M5
 *       solo lee sus propias tablas ({@code "Participacion"}, {@code "Intento"}).</li>
 *   <li>Se ejecuta en la transacción de escritura de M4, que ya bloqueó la fila de la sesión. Cada candidato se cierra
 *       con el caso de uso de M5 ({@code ParticipacionFacade.cerrarSiVencido}), que bloquea la fila del intento y
 *       vuelve a decidir el vencimiento con {@code clock_timestamp()}: un intento finalizado entretanto por el
 *       estudiante o todavía vigente no se toca. Los candidatos se procesan en orden de {@code idIntento}.</li>
 *   <li>No reduce ni cambia ningún límite persistido, y no cierra intentos vigentes (cierre anticipado).</li>
 *   <li>Un error se propaga como {@code M4-PROVEEDOR-NO-DISPONIBLE}: la transacción se revierte y la sesión no se
 *       cierra. Nunca se ignora.</li>
 * </ul>
 */
public final class CierreIntentosVencidosDesdeM5 implements CierreParticipacionesPort {

    static final String VENCIDOS_EN_CURSO = """
            SELECT i."idIntento" FROM public."Intento" i
            JOIN public."Participacion" p ON p."idParticipacion" = i."idParticipacion"
            WHERE p."idConvocatoria" = ANY (?) AND i."estado" = 'EnCurso' AND i."limiteTemporal" < clock_timestamp()
            ORDER BY i."idIntento"
            """;

    private final ConvocatoriasSesionPort convocatorias;
    private final JdbcTemplate jdbc;
    private final Predicate<ReferenciaIntento> cerrarSiVencido;

    public CierreIntentosVencidosDesdeM5(ConvocatoriasSesionPort convocatorias, JdbcTemplate jdbc,
            Predicate<ReferenciaIntento> cerrarSiVencido) {
        this.convocatorias = Objects.requireNonNull(convocatorias, "convocatorias");
        this.jdbc = Objects.requireNonNull(jdbc, "jdbc");
        this.cerrarSiVencido = Objects.requireNonNull(cerrarSiVencido, "cerrarSiVencido");
    }

    @Override
    public int cerrarIntentosVencidos(UUID sesionId) {
        if (sesionId == null) {
            throw new IllegalArgumentException("La sesión es obligatoria");
        }
        if (!TransactionSynchronizationManager.isActualTransactionActive()
                || TransactionSynchronizationManager.isCurrentTransactionReadOnly()) {
            throw new IllegalStateException("El cierre de intentos M4–M5 requiere una transacción de escritura");
        }
        try {
            List<UUID> deLaSesion = convocatorias.idsPorSesion(sesionId);
            if (deLaSesion == null || deLaSesion.stream().anyMatch(Objects::isNull)) {
                throw new IllegalStateException("M4 devolvió convocatorias incompletas para la sesión");
            }
            if (deLaSesion.isEmpty()) {
                return 0;
            }
            UUID[] ids = deLaSesion.toArray(UUID[]::new);
            List<UUID> candidatos = jdbc.query(conexion -> {
                var sentencia = conexion.prepareStatement(VENCIDOS_EN_CURSO);
                sentencia.setArray(1, conexion.createArrayOf("uuid", ids));
                return sentencia;
            }, (fila, n) -> fila.getObject(1, UUID.class));
            int cerrados = 0;
            for (UUID intento : candidatos) {
                if (cerrarSiVencido.test(new ReferenciaIntento(intento.toString()))) {
                    cerrados++;
                }
            }
            return cerrados;
        } catch (RuntimeException causa) {
            var error = new ReglaNegocioException("M4-PROVEEDOR-NO-DISPONIBLE",
                    "No se pudieron cerrar los intentos vencidos de la sesión en M5");
            error.initCause(causa);
            throw error;
        }
    }
}
