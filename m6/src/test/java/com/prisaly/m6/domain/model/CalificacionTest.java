package com.prisaly.m6.domain.model;

import static org.junit.jupiter.api.Assertions.*;
import java.lang.reflect.Modifier;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** Prepared only: M6 requires an authorized JUnit dependency before test compilation. */
class CalificacionTest {
    private static final UUID ID = UUID.randomUUID(), ATTEMPT = UUID.randomUUID(), RUBRIC = UUID.randomUUID();
    private static final Instant DATE = Instant.parse("2026-01-01T00:00:00.123456Z");

    @Test
    void preservesEverySuppliedValueIncludingOpaqueJsonAndNullablePolicy() {
        BigDecimal value = new BigDecimal("12.500");
        String json = " {\"opaque\":[true,null,1.00],\"text\":\"unchanged\"} ";
        var grade = new Calificacion(ID, ATTEMPT, RUBRIC, value, DATE, json, null);
        assertEquals(ID, grade.idCalificacion()); assertEquals(ATTEMPT, grade.idIntento());
        assertEquals(RUBRIC, grade.idRubrica()); assertSame(value, grade.valor());
        assertEquals(new BigDecimal("12.500"), grade.valor()); assertEquals(3, grade.valor().scale());
        assertEquals(DATE, grade.fechaCalculo()); assertEquals(json, grade.detalleEvaluacion());
        assertNull(grade.versionPolitica());
        assertEquals("null", grade(value, DATE, "null", "").detalleEvaluacion());
        assertEquals("", grade(value, DATE, "{}", "").versionPolitica());
    }

    @Test
    void rejectsEachMandatoryNullWithoutGeneratingDefaults() {
        BigDecimal value = BigDecimal.ONE;
        assertThrows(NullPointerException.class, () -> new Calificacion(null, ATTEMPT, RUBRIC, value, DATE, "{}", null));
        assertThrows(NullPointerException.class, () -> new Calificacion(ID, null, RUBRIC, value, DATE, "{}", null));
        assertThrows(NullPointerException.class, () -> new Calificacion(ID, ATTEMPT, null, value, DATE, "{}", null));
        assertThrows(NullPointerException.class, () -> grade(null, DATE, "{}", null));
        assertThrows(NullPointerException.class, () -> grade(value, null, "{}", null));
        assertThrows(NullPointerException.class, () -> grade(value, DATE, null, null));
    }

    @Test
    void acceptsPhysicalNumericRangeWithoutInventingPositiveGradeRule() {
        for (String text : List.of("0", "0.0000", "-1.25", "999999.99", "-999999.99", "12.500", "1E+5")) {
            BigDecimal value = new BigDecimal(text);
            assertSame(value, grade(value, DATE, "{}", null).valor());
        }
    }

    @Test
    void rejectsOverflowAndValuesRequiringRoundingWithoutChangingInput() {
        for (String text : List.of("1000000", "-1000000", "999999.991", "1.001", "-0.001")) {
            BigDecimal value = new BigDecimal(text);
            assertThrows(IllegalArgumentException.class, () -> grade(value, DATE, "{}", null));
            assertEquals(text, value.toString());
        }
    }

    @Test
    void policyLengthCountsUnicodeCodePointsAndDoesNotRatifyPolicy() {
        String policy = "\uD83D\uDE00".repeat(50);
        assertEquals(100, policy.length());
        assertEquals(policy, grade(BigDecimal.ONE, DATE, "{}", policy).versionPolitica());
        assertThrows(IllegalArgumentException.class, () -> grade(BigDecimal.ONE, DATE, "{}", policy + "a"));
        assertThrows(IllegalArgumentException.class, () -> grade(BigDecimal.ONE, DATE, "{}", "a".repeat(51)));
    }

    @Test
    void timestampCannotBeSilentlyRounded() {
        Instant nanos = Instant.parse("2026-01-01T00:00:00.123456789Z");
        assertThrows(IllegalArgumentException.class, () -> grade(BigDecimal.ONE, nanos, "{}", null));
        assertEquals(123456789, nanos.getNano());
    }

    @Test
    void recordExposesOnlyImmutableComponentTypesAndFinalInstanceFields() {
        assertTrue(Calificacion.class.isRecord());
        assertTrue(Modifier.isFinal(Calificacion.class.getModifiers()));
        assertTrue(Arrays.stream(Calificacion.class.getDeclaredFields()).filter(f -> !Modifier.isStatic(f.getModifiers()))
                .allMatch(f -> Modifier.isPrivate(f.getModifiers()) && Modifier.isFinal(f.getModifiers())));
        assertEquals(List.of(UUID.class, UUID.class, UUID.class, BigDecimal.class, Instant.class, String.class, String.class),
                Arrays.stream(Calificacion.class.getRecordComponents()).map(c -> c.getType()).toList());
        assertEquals(grade(BigDecimal.ONE, DATE, "{}", null), grade(BigDecimal.ONE, DATE, "{}", null));
    }

    private static Calificacion grade(BigDecimal value, Instant date, String json, String policy) {
        return new Calificacion(ID, ATTEMPT, RUBRIC, value, date, json, policy);
    }
}
