package com.prisaly.m5.application.servicio;

import com.prisaly.m5.application.IntentoNoEncontradoException;
import com.prisaly.m5.application.ReferenciaIntento;
import com.prisaly.m5.application.ResumenIntento;
import com.prisaly.m5.application.port.RepositorioIntentos;
import com.prisaly.m5.application.port.RepositorioIntentos.InstanteDeCierre;
import com.prisaly.m5.domain.intento.Intento;
import com.prisaly.m5.domain.intento.MotivoFinalizacion;

/**
 * Caso de uso: finalizar un intento con el motivo indicado.
 *
 * <p>Solo cierra la ejecución del intento. No evalúa ni califica (M6), no
 * publica resultados y no inicia una repetición: finalizar, evaluar y
 * publicar son operaciones distintas. Avisar a M6 de que hay un intento
 * finalizado queda pendiente de su contrato y debe ocurrir después de
 * persistir.
 *
 * <p><b>V1-c (DC-BE-M5-CIERRE-IMPLEMENTACION-45).</b> El vencimiento lo decide este caso de uso con el reloj
 * de PostgreSQL leído con la fila del intento bloqueada ({@link RepositorioIntentos#instanteDeCierre}), y el cierre
 * se graba con ese mismo instante: una entrega del estudiante después del límite queda como
 * {@link MotivoFinalizacion#TIEMPO_VENCIDO}, y antes del límite como la entrega pedida. Las respuestas no cambian:
 * finalizar nunca escribe respuestas. {@link #cerrarSiVencido} es la variante del cierre docente: solo cierra si el
 * límite ya pasó y nunca falla por un intento ya finalizado.
 *
 * <p>Reintento idempotente (D13, preparación técnica DC-CLAUDE-BE-COMP-03): si el
 * intento ya está Finalizado con el <b>mismo</b> motivo, se devuelve el resumen
 * persistido sin guardar nada, de modo que el cierre original (estado, motivo y
 * marca {@code fin}) se conserva. También es reintento una entrega del estudiante
 * sobre un intento ya cerrado por {@code TIEMPO_VENCIDO} cuando el límite ya pasó
 * (V1-c: esa entrega también sería un cierre por tiempo): se devuelve ese cierre.
 * Con cualquier otro motivo <b>distinto</b> no es un reintento:
 * el dominio sigue rechazándolo y el conflicto se mantiene. El intento se carga con
 * el bloqueo de escritura del repositorio, así que dos finalizaciones simultáneas se
 * serializan y la segunda observa el cierre de la primera.
 */
public final class FinalizarIntentoServicio {

    private final RepositorioIntentos repositorio;

    public FinalizarIntentoServicio(RepositorioIntentos repositorio) {
        this.repositorio = CargaIntento.requerir(repositorio);
    }

    /**
     * @throws IntentoNoEncontradoException si el intento no existe
     * @throws com.prisaly.m5.domain.ParticipacionDominioException si el dominio
     *         rechaza la finalización (motivo nulo o intento ya finalizado con
     *         otro motivo)
     */
    public ResumenIntento finalizar(ReferenciaIntento referencia, MotivoFinalizacion motivo) {
        Intento intento = CargaIntento.cargar(repositorio, referencia);
        if (esReintento(intento, motivo, referencia)) {
            return repositorio.resumir(referencia, intento);
        }
        if (motivo == null || intento.estaFinalizado()) {
            intento.finalizar(motivo); // el dominio rechaza: motivo nulo o intento ya cerrado con otro motivo
            throw new IllegalStateException("El dominio debía rechazar la finalización");
        }
        InstanteDeCierre reloj = repositorio.instanteDeCierre(referencia);
        intento.finalizar(motivoEfectivo(motivo, reloj));
        repositorio.guardar(referencia, intento, reloj.ahora());
        return repositorio.resumir(referencia, intento);
    }

    /**
     * V1-c, cierre docente: cierra con {@code TIEMPO_VENCIDO} solo si el intento sigue en curso y su límite ya pasó
     * en el instante de PostgreSQL leído con la fila bloqueada. Un intento ya finalizado (por ejemplo, por una entrega
     * concurrente del estudiante) o todavía vigente no se toca.
     *
     * @return {@code true} si este llamado cerró el intento
     */
    public boolean cerrarSiVencido(ReferenciaIntento referencia) {
        Intento intento = CargaIntento.cargar(repositorio, referencia);
        if (intento.estaFinalizado()) {
            return false;
        }
        InstanteDeCierre reloj = repositorio.instanteDeCierre(referencia);
        if (!reloj.vencido()) {
            return false;
        }
        intento.finalizar(MotivoFinalizacion.TIEMPO_VENCIDO);
        repositorio.guardar(referencia, intento, reloj.ahora());
        return true;
    }

    /** Solo la entrega del estudiante cambia de motivo; un cierre por tiempo pedido por el invocador se respeta. */
    private static MotivoFinalizacion motivoEfectivo(MotivoFinalizacion pedido, InstanteDeCierre reloj) {
        return pedido == MotivoFinalizacion.ENTREGA_ESTUDIANTE && reloj.vencido()
                ? MotivoFinalizacion.TIEMPO_VENCIDO : pedido;
    }

    /**
     * Reintento = la petición tendría hoy el mismo motivo efectivo que el cierre persistido. Una entrega del estudiante
     * sobre un intento cerrado por tiempo solo lo es si el límite ya pasó en el instante de PostgreSQL leído ahora con
     * la fila bloqueada (V1-c); antes del límite sigue siendo un motivo distinto y el dominio la rechaza (D13).
     */
    private boolean esReintento(Intento intento, MotivoFinalizacion motivo, ReferenciaIntento referencia) {
        if (motivo == null || !intento.estaFinalizado()) {
            return false;
        }
        MotivoFinalizacion persistido = intento.motivoFinalizacion().orElse(null);
        if (motivo == persistido) {
            return true;
        }
        return motivo == MotivoFinalizacion.ENTREGA_ESTUDIANTE && persistido == MotivoFinalizacion.TIEMPO_VENCIDO
                && repositorio.instanteDeCierre(referencia).vencido();
    }
}
