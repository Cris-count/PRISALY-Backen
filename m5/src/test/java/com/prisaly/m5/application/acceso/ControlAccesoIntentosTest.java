package com.prisaly.m5.application.acceso;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.prisaly.m5.application.DatoAplicacionInvalidoException;
import com.prisaly.m5.application.IntentoNoEncontradoException;
import com.prisaly.m5.application.ReferenciaIntento;

/**
 * Los dobles de identidad y titularidad existen solo en esta prueba: no hay
 * implementaciones de producción hasta DD-02 (M1) y el contrato de M4.
 */
@DisplayName("ControlAccesoIntentos (falla cerrada)")
class ControlAccesoIntentosTest {

    private static final ReferenciaIntento INTENTO = new ReferenciaIntento("I-1");
    private static final IdentificadorEstudiante ANA = new IdentificadorEstudiante("E-ana");
    private static final IdentificadorEstudiante LUIS = new IdentificadorEstudiante("E-luis");

    private Optional<IdentificadorEstudiante> autenticado = Optional.empty();
    private final Map<ReferenciaIntento, IdentificadorEstudiante> titulares = new HashMap<>();

    private final ControlAccesoIntentos control = new ControlAccesoIntentos(
            () -> autenticado, intento -> Optional.ofNullable(titulares.get(intento)));

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
    @DisplayName("un intento sin titular conocido se trata como inexistente")
    void intentoSinTitular() {
        autenticado = Optional.of(ANA);

        assertThrows(IntentoNoEncontradoException.class, () -> control.verificar(INTENTO));
    }

    @Test
    @DisplayName("la identidad se verifica antes de consultar la titularidad")
    void identidadAntesQueTitularidad() {
        ControlAccesoIntentos conTitularidadQueFalla = new ControlAccesoIntentos(
                Optional::empty, intento -> {
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
                }, intento -> Optional.of(ANA));

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
}
