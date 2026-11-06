package com.prisaly.m5.application.acceso;

import com.prisaly.m5.application.ConvocatoriaNoEncontradaException;
import com.prisaly.m5.application.DatoAplicacionInvalidoException;
import com.prisaly.m5.application.IntentoNoEncontradoException;
import com.prisaly.m5.application.ReferenciaConvocatoria;
import com.prisaly.m5.application.ReferenciaIntento;
import com.prisaly.m5.application.ReferenciaSesion;

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

    /**
     * Verificación previa a iniciar o reanudar: estudiante autenticado (M1) y
     * titular de la convocatoria (M4). No evalúa elegibilidad.
     *
     * @throws EstudianteNoIdentificadoException si no hay estudiante autenticado
     * @throws ConvocatoriaNoEncontradaException si la convocatoria no existe o
     *     no es del estudiante: no se distinguen, para no revelar convocatorias ajenas
     */
    public IdentificadorEstudiante verificarConvocatoria(ReferenciaConvocatoria convocatoria) {
        if (convocatoria == null) {
            throw new DatoAplicacionInvalidoException("La referencia de la convocatoria es obligatoria");
        }
        IdentificadorEstudiante estudiante = identidad.actual().orElseThrow(EstudianteNoIdentificadoException::new);
        if (titularidad.deConvocatoria(convocatoria, estudiante) == Titularidad.PROPIO) {
            return estudiante;
        }
        throw new ConvocatoriaNoEncontradaException();
    }

    /**
     * Resolución de la convocatoria propia en una sesión (D01): estudiante autenticado (M1) y una
     * única convocatoria suya en la sesión, según el contrato de M4. No evalúa elegibilidad.
     *
     * @throws EstudianteNoIdentificadoException si no hay estudiante autenticado
     * @throws ConvocatoriaNoEncontradaException si la sesión no tiene una convocatoria del
     *     estudiante: inexistente y ajena no se distinguen
     */
    public ReferenciaConvocatoria convocatoriaPropiaEnSesion(ReferenciaSesion sesion) {
        if (sesion == null) {
            throw new DatoAplicacionInvalidoException("La referencia de la sesión es obligatoria");
        }
        IdentificadorEstudiante estudiante = identidad.actual().orElseThrow(EstudianteNoIdentificadoException::new);
        java.util.Optional<ReferenciaConvocatoria> convocatoria = titularidad.convocatoriaEnSesion(sesion, estudiante);
        if (convocatoria == null || convocatoria.isEmpty()) {
            throw new ConvocatoriaNoEncontradaException();
        }
        return convocatoria.get();
    }
}
