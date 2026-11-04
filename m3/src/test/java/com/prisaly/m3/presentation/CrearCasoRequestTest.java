package com.prisaly.m3.presentation;

import static org.junit.jupiter.api.Assertions.*;

import com.prisaly.m3.domain.model.CasoEstado;
import com.prisaly.m3.presentation.dto.CrearCasoRequest;
import com.prisaly.m3.presentation.dto.InvalidCasoRequestException;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class CrearCasoRequestTest {
    private static final UUID CASE = UUID.fromString("11111111-1111-4111-8111-111111111111");
    // Represents the identity returned by the trusted current-user port in this fixture.
    private static final UUID CURRENT_USER = UUID.fromString("22222222-2222-4222-8222-222222222222");

    @Test
    void minimalRequestBuildsPersistableRootWithoutInventedMetadataOrContent() {
        var request = CrearCasoRequest.fromJson(json("\"titulo\":\"Borrador de prueba\",\"tiempoEstimadoSegundos\":300"));
        var draft = request.toBuilder(CURRENT_USER).buildDraft();
        assertEquals(CASE.toString(), draft.id());
        assertEquals(CURRENT_USER.toString(), draft.docenteId());
        assertEquals(CasoEstado.BORRADOR, draft.estado());
        assertEquals(300, draft.tiempoEstimadoSegundos());
        assertTrue(draft.activo()); // Existing Builder/V001 default, not an HTTP assignment.
        assertTrue(draft.escenarios().isEmpty());
        assertNull(draft.rubrica());
        assertNull(draft.fechaCreacion());
        assertNull(draft.fechaActualizacion());
        assertNull(draft.fechaBloqueoAcademico());
        assertFalse(draft.contenidoEvaluableBloqueado());
    }

    @Test
    void optionalNarrativesAndUnicodeLimitsArePreserved() {
        String title = "\uD83D\uDE00".repeat(150);
        var request = CrearCasoRequest.fromJson(json("\"titulo\":\"" + title
                + "\",\"tiempoEstimadoSegundos\":1,\"historia\":\"Línea\\nsegunda\",\"desarrolloSituacional\":null"));
        var draft = request.toBuilder(CURRENT_USER).buildDraft();
        assertEquals(title, draft.titulo());
        assertEquals("Línea\nsegunda", draft.historia());
        assertNull(draft.desarrolloSituacional());
        assertThrows(InvalidCasoRequestException.class,
                () -> new CrearCasoRequest(CASE, title + "x", 1, null, null));
    }

    @Test
    void rejectsCoercionsMissingRequiredValuesInvalidUuidsDuplicatesAndTrailingDocuments() {
        for (String seconds : new String[] {"0", "-1", "null", "1.5", "1.0", "\"300\"", "2147483648", "true"}) {
            invalid(json("\"titulo\":\"Prueba\",\"tiempoEstimadoSegundos\":" + seconds));
        }
        invalid("{}");
        invalid("null");
        invalid("[]");
        invalid("{");
        invalid(json("\"titulo\":\"Prueba\""));
        invalid(json("\"titulo\":\" \",\"tiempoEstimadoSegundos\":1"));
        invalid(json("\"titulo\":42,\"tiempoEstimadoSegundos\":1"));
        invalid(json("\"titulo\":\"Prueba\",\"titulo\":\"Otro\",\"tiempoEstimadoSegundos\":1"));
        invalid(json("\"titulo\":\"Prueba\",\"tiempoEstimadoSegundos\":1") + "{}");
        invalid("{\"idCaso\":\"1-1-1-1-1\",\"titulo\":\"Prueba\",\"tiempoEstimadoSegundos\":1}");
        invalid("{\"idCaso\":null,\"titulo\":\"Prueba\",\"tiempoEstimadoSegundos\":1}");
    }

    @Test
    void rejectsNulAndUnpairedSurrogatesThatCannotBeStoredAsPostgresText() {
        for (String invalid : new String[] {"\\u0000", "\\uD800", "\\uDC00"}) {
            invalid(json("\"titulo\":\"" + invalid + "\",\"tiempoEstimadoSegundos\":1"));
            invalid(json("\"titulo\":\"Prueba\",\"tiempoEstimadoSegundos\":1,\"historia\":\"" + invalid + "\""));
            invalid(json("\"titulo\":\"Prueba\",\"tiempoEstimadoSegundos\":1,\"desarrolloSituacional\":\"" + invalid + "\""));
        }
    }

    @Test
    void rejectsOwnerAndEveryUnsupportedStructureRatherThanIgnoringThem() {
        for (String extra : new String[] {"\"docenteId\":null", "\"userId\":null", "\"estado\":\"Publicado\"",
                "\"activo\":false", "\"fechaCreacion\":null", "\"escenarios\":[]", "\"rubrica\":null"}) {
            invalid(json("\"titulo\":\"Prueba\",\"tiempoEstimadoSegundos\":1," + extra));
        }
    }

    private static String json(String fields) {
        return "{\"idCaso\":\"" + CASE + "\"," + fields + "}";
    }

    private static void invalid(String json) {
        assertThrows(InvalidCasoRequestException.class, () -> CrearCasoRequest.fromJson(json));
    }
}
