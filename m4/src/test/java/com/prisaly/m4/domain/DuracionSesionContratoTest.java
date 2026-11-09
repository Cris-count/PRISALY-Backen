package com.prisaly.m4.domain;

import static org.junit.jupiter.api.Assertions.*;

import com.prisaly.m4.domain.model.SesionAcademica;
import com.prisaly.m4.domain.rule.ReglaNegocioException;
import com.prisaly.m4.presentation.dto.CrearSesionRequest;
import jakarta.validation.Validation;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;

/** Caracteriza F3-04 vigente; no ratifica la restricción RF-18 aún contradictoria. */
class DuracionSesionContratoTest {
    private static final Instant INICIO = Instant.parse("2030-11-05T09:00:00Z");
    private static final Instant FIN = INICIO.plusSeconds(3600);

    @ParameterizedTest
    @ValueSource(ints = {30, 60, 120})
    void actualizarProgramacionConservaLaDuracionExplicita(int duracion) {
        var sesion = SesionAcademica.crear(UUID.randomUUID(), UUID.randomUUID(),
                "Inicial", null, INICIO, FIN, duracion);
        sesion.actualizarProgramacion("Editada", "Mensaje", INICIO.plusSeconds(60), FIN);
        assertEquals(duracion, sesion.getTiempoMaximoParticipacionSegundos());
    }

    @ParameterizedTest
    @ValueSource(ints = {Integer.MIN_VALUE, -1, 0})
    void dominioRechazaDuracionNoPositiva(int duracion) {
        var error = assertThrows(ReglaNegocioException.class,
                () -> SesionAcademica.crear(UUID.randomUUID(), UUID.randomUUID(),
                        "Inicial", null, INICIO, FIN, duracion));
        assertEquals("M4-DURACION-INVALIDA", error.getCodigo());
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(ints = {Integer.MIN_VALUE, -1, 0})
    void dtoRechazaDuracionAusenteONoPositiva(Integer duracion) {
        try (var factory = Validation.buildDefaultValidatorFactory()) {
            var solicitud = new CrearSesionRequest(UUID.randomUUID(), "Inicial", null,
                    INICIO, FIN, duracion);
            var errores = factory.getValidator().validate(solicitud);
            assertEquals(1, errores.size());
            assertEquals("tiempoMaximoParticipacionSegundos",
                    errores.iterator().next().getPropertyPath().toString());
        }
    }
}
