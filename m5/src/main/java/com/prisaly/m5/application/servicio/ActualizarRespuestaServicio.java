package com.prisaly.m5.application.servicio;

import com.prisaly.m5.application.IntentoNoEncontradoException;
import com.prisaly.m5.application.ReferenciaIntento;
import com.prisaly.m5.application.ResumenIntento;
import com.prisaly.m5.application.port.RepositorioIntentos;
import com.prisaly.m5.domain.intento.Intento;
import com.prisaly.m5.domain.respuesta.ReferenciaPregunta;
import com.prisaly.m5.domain.respuesta.ValorRespuesta;

/**
 * Caso de uso PROVISIONAL: reemplazar una respuesta mientras el intento siga
 * EnCurso.
 *
 * <p>Depende de la decisión académica pendiente sobre si una respuesta puede
 * cambiarse antes de finalizar. Está aislado en su propia clase para poder
 * retirarlo, junto con {@link Intento#actualizarRespuesta}, sin afectar el
 * registro inicial. (La columna {@code "Respuesta"."revision"} de la rama de
 * M1 sugiere que el modelo lo contempla, pero no lo ratifica.)
 */
public final class ActualizarRespuestaServicio {

    private final RepositorioIntentos repositorio;

    public ActualizarRespuestaServicio(RepositorioIntentos repositorio) {
        this.repositorio = CargaIntento.requerir(repositorio);
    }

    /**
     * @throws IntentoNoEncontradoException si el intento no existe
     * @throws com.prisaly.m5.domain.ParticipacionDominioException si el dominio
     *         rechaza la actualización (intento finalizado, sin respuesta previa
     *         o datos inválidos)
     */
    public ResumenIntento actualizar(ReferenciaIntento referencia, ReferenciaPregunta pregunta, ValorRespuesta valor) {
        Intento intento = CargaIntento.cargar(repositorio, referencia);
        intento.actualizarRespuesta(pregunta, valor);
        repositorio.guardar(referencia, intento);
        return ResumenIntento.de(referencia, intento);
    }
}
