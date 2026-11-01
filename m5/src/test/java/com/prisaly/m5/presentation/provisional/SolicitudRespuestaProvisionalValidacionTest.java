package com.prisaly.m5.presentation.provisional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Set;
import java.util.stream.Collectors;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

/** Validación Bean Validation de la entrada provisional (Hibernate Validator). */
@DisplayName("SolicitudRespuestaProvisional: validación de entrada")
class SolicitudRespuestaProvisionalValidacionTest {

    private static final String UUID_VALIDO = "6f1c2c1e-2d7b-4c55-9b0b-0d3e1f2a3b4c";

    private static ValidatorFactory fabrica;
    private static Validator validador;

    @BeforeAll
    static void crearValidador() {
        fabrica = Validation.buildDefaultValidatorFactory();
        validador = fabrica.getValidator();
    }

    @AfterAll
    static void cerrarValidador() {
        fabrica.close();
    }

    @ParameterizedTest(name = "acepta {0}")
    @CsvSource({
            "6f1c2c1e-2d7b-4c55-9b0b-0d3e1f2a3b4c, a1b2c3d4-e5f6-4789-8abc-def012345678",
            "6F1C2C1E-2D7B-4C55-9B0B-0D3E1F2A3B4C, A1B2C3D4-E5F6-4789-8ABC-DEF012345678"})
    void aceptaUuids(String pregunta, String opcion) {
        assertTrue(validador.validate(new SolicitudRespuestaProvisional(pregunta, opcion)).isEmpty());
    }

    @ParameterizedTest(name = "rechaza la pregunta \"{0}\"")
    @NullAndEmptySource
    @ValueSource(strings = {" ", "P-1", "6f1c2c1e2d7b4c559b0b0d3e1f2a3b4c", "6f1c2c1e-2d7b-4c55-9b0b-0d3e1f2a3b4c "})
    void rechazaPreguntaInvalida(String pregunta) {
        Set<ConstraintViolation<SolicitudRespuestaProvisional>> violaciones =
                validador.validate(new SolicitudRespuestaProvisional(pregunta, UUID_VALIDO));

        assertTrue(violaciones.stream().allMatch(v -> v.getPropertyPath().toString().equals("idPregunta")));
        assertTrue(!violaciones.isEmpty());
    }

    @ParameterizedTest(name = "rechaza la opción \"{0}\"")
    @NullAndEmptySource
    @ValueSource(strings = {"  ", "Opción A", "zzzzzzzz-zzzz-zzzz-zzzz-zzzzzzzzzzzz"})
    void rechazaOpcionInvalida(String opcion) {
        Set<ConstraintViolation<SolicitudRespuestaProvisional>> violaciones =
                validador.validate(new SolicitudRespuestaProvisional(UUID_VALIDO, opcion));

        assertEquals(Set.of("idOpcionSeleccionada"),
                violaciones.stream().map(v -> v.getPropertyPath().toString()).collect(Collectors.toSet()));
    }
}
