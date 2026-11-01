package com.prisaly.m5.application.acceso;

import com.prisaly.m5.application.DatoAplicacionInvalidoException;
import com.prisaly.m5.application.IntentoNoEncontradoException;
import com.prisaly.m5.application.ReferenciaIntento;

/**
 * Verificación de identidad y titularidad previa a cualquier operación de
 * estudiante sobre un intento. Falla de forma cerrada: sin identidad, sin
 * titular conocido o con un titular distinto, no hay acceso.
 *
 * <p>Preparada y probada, pero sin conectar a la fachada ni a controladores:
 * sus puertos no tienen implementación de producción hasta que existan DD-02
 * (M1) y el contrato de titularidad (M4). Debe invocarse antes de cargar el
 * intento.
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
     * @throws IntentoNoEncontradoException si el intento no tiene titular conocido
     * @throws AccesoIntentoDenegadoException si el titular es otro estudiante
     */
    public IdentificadorEstudiante verificar(ReferenciaIntento intento) {
        if (intento == null) {
            throw new DatoAplicacionInvalidoException("La referencia del intento es obligatoria");
        }
        IdentificadorEstudiante estudiante = identidad.actual().orElseThrow(EstudianteNoIdentificadoException::new);
        IdentificadorEstudiante titular = titularidad.titularDe(intento)
                .orElseThrow(() -> new IntentoNoEncontradoException(intento));
        if (!titular.equals(estudiante)) {
            throw new AccesoIntentoDenegadoException();
        }
        return estudiante;
    }
}
