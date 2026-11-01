package com.prisaly.m5.application.servicio;

import com.prisaly.m5.application.DatoAplicacionInvalidoException;
import com.prisaly.m5.application.IntentoNoEncontradoException;
import com.prisaly.m5.application.ReferenciaIntento;
import com.prisaly.m5.application.port.RepositorioIntentos;
import com.prisaly.m5.domain.intento.Intento;

/** Carga común de un intento para los servicios de aplicación. */
final class CargaIntento {

    private CargaIntento() {
    }

    static Intento cargar(RepositorioIntentos repositorio, ReferenciaIntento referencia) {
        if (referencia == null) {
            throw new DatoAplicacionInvalidoException("La referencia del intento es obligatoria");
        }
        return repositorio.buscar(referencia).orElseThrow(() -> new IntentoNoEncontradoException(referencia));
    }

    static RepositorioIntentos requerir(RepositorioIntentos repositorio) {
        if (repositorio == null) {
            throw new DatoAplicacionInvalidoException("El repositorio de intentos es obligatorio");
        }
        return repositorio;
    }
}
