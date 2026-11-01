package com.prisaly.m6.domain.model;

import static org.junit.jupiter.api.Assertions.*;
import java.lang.reflect.Modifier;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** Prepared only: no test dependencies have been added. */
class RetroalimentacionGeneralTest {
    private static final UUID ID = UUID.randomUUID(), PARTICIPATION = UUID.randomUUID(), TEACHER = UUID.randomUUID();
    private static final Instant DATE = Instant.parse("2026-01-01T00:00:00.123456Z");

    @Test
    void preservesAllFieldsWithoutTrimmingOrInventingContentRules() {
        for (String content : List.of("Feedback", "", "  ", " Observacion\n\uD83D\uDE00 ")) {
            var feedback = new RetroalimentacionGeneral(ID, PARTICIPATION, TEACHER, content, DATE);
            assertEquals(ID, feedback.idRetroalimentacion()); assertEquals(PARTICIPATION, feedback.idParticipacion());
            assertEquals(TEACHER, feedback.idDocente()); assertEquals(content, feedback.contenido());
            assertEquals(DATE, feedback.fechaRegistro());
        }
    }

    @Test
    void rejectsEachMandatoryNullWithoutGeneratingDefaults() {
        assertThrows(NullPointerException.class, () -> new RetroalimentacionGeneral(null, PARTICIPATION, TEACHER, "Text", DATE));
        assertThrows(NullPointerException.class, () -> new RetroalimentacionGeneral(ID, null, TEACHER, "Text", DATE));
        assertThrows(NullPointerException.class, () -> new RetroalimentacionGeneral(ID, PARTICIPATION, null, "Text", DATE));
        assertThrows(NullPointerException.class, () -> new RetroalimentacionGeneral(ID, PARTICIPATION, TEACHER, null, DATE));
        assertThrows(NullPointerException.class, () -> new RetroalimentacionGeneral(ID, PARTICIPATION, TEACHER, "Text", null));
    }

    @Test
    void rejectsTimestampRequiringPrecisionLoss() {
        assertThrows(IllegalArgumentException.class, () -> new RetroalimentacionGeneral(ID, PARTICIPATION, TEACHER,
                "Text", Instant.parse("2026-01-01T00:00:00.123456789Z")));
    }

    @Test
    void recordContainsOnlyImmutableTypesAndFinalInstanceFields() {
        assertTrue(RetroalimentacionGeneral.class.isRecord());
        assertTrue(Modifier.isFinal(RetroalimentacionGeneral.class.getModifiers()));
        assertTrue(Arrays.stream(RetroalimentacionGeneral.class.getDeclaredFields()).filter(f -> !Modifier.isStatic(f.getModifiers()))
                .allMatch(f -> Modifier.isPrivate(f.getModifiers()) && Modifier.isFinal(f.getModifiers())));
        assertEquals(List.of(UUID.class, UUID.class, UUID.class, String.class, Instant.class),
                Arrays.stream(RetroalimentacionGeneral.class.getRecordComponents()).map(c -> c.getType()).toList());
        assertEquals(new RetroalimentacionGeneral(ID, PARTICIPATION, TEACHER, "Text", DATE),
                new RetroalimentacionGeneral(ID, PARTICIPATION, TEACHER, "Text", DATE));
    }
}
