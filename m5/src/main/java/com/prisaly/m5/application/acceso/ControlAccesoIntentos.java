package com.prisaly.m5.application.acceso;

import com.prisaly.m5.application.DatoAplicacionInvalidoException;
import com.prisaly.m5.application.IntentoNoEncontradoException;
import com.prisaly.m5.application.ReferenciaIntento;

/**
 * Verificación de identidad y titularidad previa a cualquier operación de
 * estudiante sobre un intento. Falla de forma cerrada: sin identidad, con un
 * intento inexistente, con un intento ajeno o ante una respuesta desconocida
 * del proveedor, no hay acceso. Los errores de los puertos se propagan.
 *
 * <p>La usa {@link AccesoProtegidoParticipacion}. Debe invocarse antes de
 * cargar el intento.
 */
public final class ControlAccesoIntentos {

    private final IdentidadEstudianteActual identidad;
    private final TitularidadIntentos titularidad;

    public ControlAccesoIntentos(IdentidadEstudianteActual identidad, TitularidadIntentos titularidad) {
        if (identidad == null || titularidad == null) {
            throw new DatoAplicacionInvalidoException("El control de acceso requiere identidad y titularidad");
        }
        this.identidad = identidad;
        this.titularidad = titularidad;
    }

    /**
     * @throws EstudianteNoIdentificadoException si no hay estudiante autenticado
     * @throws IntentoNoEncontradoException si el intento no existe
     * @throws AccesoIntentoDenegadoException si el intento es de otro estudiante
     *     (403 o 404: decisión 4 pendiente)
     */
    public IdentificadorEstudiante verificar(ReferenciaIntento intento) {
        if (intento == null) {
            throw new DatoAplicacionInvalidoException("La referencia del intento es obligatoria");
        }
        IdentificadorEstudiante estudiante = identidad.actual().orElseThrow(EstudianteNoIdentificadoException::new);
        Titularidad relacion = titularidad.de(intento, estudiante);
        if (relacion == Titularidad.PROPIO) {
            return estudiante;
        }
        if (relacion == Titularidad.INEXISTENTE) {
            throw new IntentoNoEncontradoException(intento);
        }
        // AJENO o una respuesta nula del proveedor: se deniega.
        throw new AccesoIntentoDenegadoException();
    }
}
