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
 */
public final class FinalizarIntentoServicio {

    private final RepositorioIntentos repositorio;

    public FinalizarIntentoServicio(RepositorioIntentos repositorio) {
        this.repositorio = CargaIntento.requerir(repositorio);
    }

    /**
     * @throws IntentoNoEncontradoException si el intento no existe
     * @throws com.prisaly.m5.domain.ParticipacionDominioException si el dominio
     *         rechaza la finalización (motivo nulo o intento ya finalizado)
     */
    public ResumenIntento finalizar(ReferenciaIntento referencia, MotivoFinalizacion motivo) {
        Intento intento = CargaIntento.cargar(repositorio, referencia);
        intento.finalizar(motivo);
        repositorio.guardar(referencia, intento);
        return ResumenIntento.de(referencia, intento);
    }
}
