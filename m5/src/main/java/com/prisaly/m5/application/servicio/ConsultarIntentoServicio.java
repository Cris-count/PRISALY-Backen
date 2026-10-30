package com.prisaly.m5.application.servicio;

import com.prisaly.m5.application.IntentoNoEncontradoException;
import com.prisaly.m5.application.ReferenciaIntento;
import com.prisaly.m5.application.ResumenIntento;
import com.prisaly.m5.application.port.RepositorioIntentos;

/**
 * Caso de uso: consultar el estado y las respuestas de un intento.
 *
 * <p>Pendiente de M1: no verifica que quien consulta sea el titular del
 * intento ni un docente autorizado. Esa restricción de visibilidad debe
 * agregarse antes de exponer la operación en un endpoint.
 */
public final class ConsultarIntentoServicio {

    private final RepositorioIntentos repositorio;

    public ConsultarIntentoServicio(RepositorioIntentos repositorio) {
        this.repositorio = CargaIntento.requerir(repositorio);
    }

    /**
     * @throws IntentoNoEncontradoException si el intento no existe
     */
    public ResumenIntento consultar(ReferenciaIntento referencia) {
        return ResumenIntento.de(referencia, CargaIntento.cargar(repositorio, referencia));
    }
}
