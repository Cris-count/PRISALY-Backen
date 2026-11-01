package com.prisaly.m5.application.acceso;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.prisaly.m5.application.DatoAplicacionInvalidoException;
import com.prisaly.m5.application.IntentoNoEncontradoException;
import com.prisaly.m5.application.ReferenciaIntento;

/**
 * Los dobles de identidad y titularidad existen solo en esta prueba. Las
 * implementaciones de producción (M1 y M4) tienen sus propias pruebas.
 */
@DisplayName("ControlAccesoIntentos (falla cerrada)")
class ControlAccesoIntentosTest {

    private static final ReferenciaIntento INTENTO = new ReferenciaIntento("I-1");
    private static final IdentificadorEstudiante ANA = new IdentificadorEstudiante("E-ana");
    private static final IdentificadorEstudiante LUIS = new IdentificadorEstudiante("E-luis");

    private Optional<IdentificadorEstudiante> autenticado = Optional.empty();
    private final Map<ReferenciaIntento, IdentificadorEstudiante> titulares = new HashMap<>();

    /** Doble de titularidad que responde como M4: no revela al titular. */
    private Titularidad relacion(ReferenciaIntento intento, IdentificadorEstudiante estudiante) {
        IdentificadorEstudiante titular = titulares.get(intento);
        if (titular == null) {
            return Titularidad.INEXISTENTE;
        }
        return titular.equals(estudiante) ? Titularidad.PROPIO : Titularidad.AJENO;
    }

    private final ControlAccesoIntentos control = new ControlAccesoIntentos(() -> autenticado, this::relacion);

    @Test
    @DisplayName("permite el acceso al titular autenticado")
    void permiteAlTitular() {
        autenticado = Optional.of(ANA);
        titulares.put(INTENTO, ANA);

        assertEquals(ANA, control.verificar(INTENTO));
    }

    @Test
    @DisplayName("sin estudiante autenticado no hay acceso, aunque el intento exista")
    void exigeIdentidad() {
        titulares.put(INTENTO, ANA);

        assertThrows(EstudianteNoIdentificadoException.class, () -> control.verificar(INTENTO));
    }

    @Test
    @DisplayName("deniega el intento de otro estudiante sin revelar su titular")
    void deniegaIntentoAjeno() {
        autenticado = Optional.of(LUIS);
        titulares.put(INTENTO, ANA);

        AccesoIntentoDenegadoException error =
                assertThrows(AccesoIntentoDenegadoException.class, () -> control.verificar(INTENTO));

        assertFalse(error.getMessage().contains(ANA.valor()));
    }

    @Test
    @DisplayName("un intento inexistente se informa como no encontrado")
    void intentoSinTitular() {
        autenticado = Optional.of(ANA);

        assertThrows(IntentoNoEncontradoException.class, () -> control.verificar(INTENTO));
    }

    @Test
    @DisplayName("la identidad se verifica antes de consultar la titularidad")
    void identidadAntesQueTitularidad() {
        ControlAccesoIntentos conTitularidadQueFalla = new ControlAccesoIntentos(
                Optional::empty, (intento, estudiante) -> {
                    throw new AssertionError("no debía consultarse la titularidad");
                });

        assertThrows(EstudianteNoIdentificadoException.class, () -> conTitularidadQueFalla.verificar(INTENTO));
    }

    @Test
    @DisplayName("un error de los puertos se propaga: nunca concede acceso por defecto")
    void propagaErroresDeLosPuertos() {
        ControlAccesoIntentos conIdentidadQueFalla = new ControlAccesoIntentos(
                () -> {
                    throw new IllegalStateException("M1 no disponible");
                }, (intento, estudiante) -> Titularidad.PROPIO);

        assertThrows(IllegalStateException.class, () -> conIdentidadQueFalla.verificar(INTENTO));
    }

    @Test
    @DisplayName("la verificación solo recibe el intento: ningún dato del cliente sustituye la identidad")
    void identidadNoLlegaDelCliente() throws NoSuchMethodException {
        Method verificar =
                ControlAccesoIntentos.class.getMethod("verificar", ReferenciaIntento.class);

        assertEquals(1, verificar.getParameterCount());
        assertEquals(1, Arrays.stream(ControlAccesoIntentos.class.getMethods())
                .filter(m -> m.getName().equals("verificar")).count());
    }

    @Test
    @DisplayName("rechaza datos nulos")
    void rechazaDatosNulos() {
        assertThrows(DatoAplicacionInvalidoException.class, () -> control.verificar(null));
        assertThrows(DatoAplicacionInvalidoException.class, () -> new ControlAccesoIntentos(null, null));
        assertThrows(DatoAplicacionInvalidoException.class, () -> new IdentificadorEstudiante(" "));
    }

    @Test
    @DisplayName("una indisponibilidad de M4 se propaga: nunca concede acceso")
    void propagaIndisponibilidadDeTitularidad() {
        autenticado = Optional.of(ANA);
        ControlAccesoIntentos conM4Caido = new ControlAccesoIntentos(
                () -> autenticado, (intento, estudiante) -> {
                    throw new IllegalStateException("M4 no disponible");
                });

        assertThrows(IllegalStateException.class, () -> conM4Caido.verificar(INTENTO));
    }

    @Test
    @DisplayName("una respuesta nula del proveedor de titularidad deniega")
    void respuestaNulaDeniega() {
        autenticado = Optional.of(ANA);
        ControlAccesoIntentos conRespuestaNula =
                new ControlAccesoIntentos(() -> autenticado, (intento, estudiante) -> null);

        assertThrows(AccesoIntentoDenegadoException.class, () -> conRespuestaNula.verificar(INTENTO));
    }

    @Test
    @DisplayName("la titularidad se consulta con el estudiante autenticado, no con otro dato")
    void consultaConElEstudianteAutenticado() {
        autenticado = Optional.of(LUIS);
        List<IdentificadorEstudiante> consultados = new ArrayList<>();
        ControlAccesoIntentos espia = new ControlAccesoIntentos(() -> autenticado, (intento, estudiante) -> {
            consultados.add(estudiante);
            return Titularidad.PROPIO;
        });

        espia.verificar(INTENTO);

        assertEquals(List.of(LUIS), consultados);
    }
}
