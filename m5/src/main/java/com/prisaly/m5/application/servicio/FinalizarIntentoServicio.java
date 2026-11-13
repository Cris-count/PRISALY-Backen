package com.prisaly.m5.application.servicio;

import com.prisaly.m5.application.IntentoNoEncontradoException;
import com.prisaly.m5.application.ReferenciaIntento;
import com.prisaly.m5.application.ResumenIntento;
import com.prisaly.m5.application.port.RepositorioIntentos;
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
 * <p>Quién decide que el tiempo venció ({@link MotivoFinalizacion#TIEMPO_VENCIDO})
 * es responsabilidad del invocador; el límite temporal depende de la sesión (M4).
 *
 * <p>Reintento idempotente (D13, preparación técnica DC-CLAUDE-BE-COMP-03): si el
 * intento ya está Finalizado con el <b>mismo</b> motivo, se devuelve el resumen
 * persistido sin guardar nada, de modo que el cierre original (estado, motivo y
 * marca {@code fin}) se conserva. Con un motivo <b>distinto</b> no es un reintento:
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
        if (esReintento(intento, motivo)) {
            return repositorio.resumir(referencia, intento);
        }
        intento.finalizar(motivo);
        repositorio.guardar(referencia, intento);
        return repositorio.resumir(referencia, intento);
    }

    private static boolean esReintento(Intento intento, MotivoFinalizacion motivo) {
        return motivo != null
                && intento.estaFinalizado()
                && intento.motivoFinalizacion().filter(motivo::equals).isPresent();
    }
}
