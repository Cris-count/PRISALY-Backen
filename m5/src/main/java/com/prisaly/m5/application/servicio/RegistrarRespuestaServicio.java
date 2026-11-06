package com.prisaly.m5.application.servicio;

import com.prisaly.m5.application.DatoAplicacionInvalidoException;
import com.prisaly.m5.application.IntentoNoEncontradoException;
import com.prisaly.m5.application.RevisionObsoletaException;
import com.prisaly.m5.domain.intento.IntentoFinalizadoException;
import com.prisaly.m5.domain.respuesta.Respuesta;
import com.prisaly.m5.application.ReferenciaIntento;
import com.prisaly.m5.application.ResumenIntento;
import com.prisaly.m5.application.port.RepositorioIntentos;
import com.prisaly.m5.domain.intento.Intento;
import com.prisaly.m5.domain.respuesta.ReferenciaPregunta;
import com.prisaly.m5.domain.respuesta.ValorRespuesta;

/**
 * Caso de uso: registrar la primera respuesta del estudiante a una pregunta.
 *
 * <p>Las reglas (intento EnCurso, una respuesta por pregunta) las aplica el
 * dominio; este servicio no las duplica. Si el dominio rechaza la operación,
 * la excepción se propaga y no se solicita guardado.
 *
 * <p>Pendiente: titularidad del estudiante (M1), pertenencia de la pregunta y
 * la opción al caso del intento (M3) y límite temporal desde la sesión (M4).
 */
public final class RegistrarRespuestaServicio {

    private final RepositorioIntentos repositorio;

    /** El decorador transaccional mantiene el bloqueo de buscar hasta el commit. */
    public Respuesta guardar(ReferenciaIntento referencia, ReferenciaPregunta pregunta,
            ValorRespuesta valor, int revisionEsperada) {
        if (revisionEsperada < 0) {
            throw new DatoAplicacionInvalidoException("La revisión esperada debe ser no negativa");
        }
        Intento intento = CargaIntento.cargar(repositorio, referencia);
        if (!intento.estado().admiteModificaciones()) {
            // Un intento cerrado se rechaza igual con cualquier revisión, sin escritura.
            throw new IntentoFinalizadoException("guardar una respuesta");
        }
        int revisionActual = intento.respuestaA(pregunta).map(Respuesta::revision).orElse(0);
        if (revisionActual != revisionEsperada) {
            throw new RevisionObsoletaException();
        }
        Respuesta respuesta = revisionActual == 0
                ? intento.registrarRespuesta(pregunta, valor)
                : intento.actualizarRespuesta(pregunta, valor, true);
        repositorio.guardar(referencia, intento);
        return respuesta;
    }

    public RegistrarRespuestaServicio(RepositorioIntentos repositorio) {
        this.repositorio = CargaIntento.requerir(repositorio);
    }

    /**
     * @throws IntentoNoEncontradoException si el intento no existe
     * @throws com.prisaly.m5.domain.ParticipacionDominioException si el dominio
     *         rechaza la respuesta (intento finalizado, duplicada o datos inválidos)
     */
    public ResumenIntento registrar(ReferenciaIntento referencia, ReferenciaPregunta pregunta, ValorRespuesta valor) {
        Intento intento = CargaIntento.cargar(repositorio, referencia);
        intento.registrarRespuesta(pregunta, valor);
        repositorio.guardar(referencia, intento);
        return ResumenIntento.de(referencia, intento);
    }
}
