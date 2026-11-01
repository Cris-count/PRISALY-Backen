package com.prisaly.m3.infrastructure.persistence.jpa;

import static org.junit.jupiter.api.Assertions.*;
import com.prisaly.m3.domain.rule.CasoDomainException;
import com.prisaly.m3.infrastructure.persistence.jpa.CasoJpaAggregateSnapshot.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class JsonbCanonicalizerTest {
    private static final UUID CASE = UUID.randomUUID();
    private static final UUID RUBRIC = UUID.randomUUID();
    private static final UUID TEACHER = UUID.randomUUID();
    private static final Instant CREATED = Instant.parse("2026-01-01T00:00:00Z");

    @Test
    void integerDecimalAndExponentRepresentationsProduceIdenticalAggregateFingerprints() {
        equivalent("1", "1.0", "1.00");
        equivalent("1e2", "100", "100.000");
        equivalent("0", "-0", "0.000", "-0.000e12");
        equivalent("123456789012345678901234567890", "123456789012345678901234567890.000");
        equivalent("1.230000e-20", "0.0000000000000000000123");
    }

    @Test
    void objectKeysAreSortedRecursivelyAndDuplicatePropertiesRetainLastValue() {
        equivalent("{\"b\":2,\"a\":1}", "{\"a\":1.00,\"b\":2.0}");
        equivalent("{\"x\":{\"b\":2,\"a\":[1,null,true]}}",
                "{\"x\":{\"a\":[1.0,null,true],\"b\":2.00}}");
        equivalent("{\"a\":false,\"a\":1.0}", "{\"a\":1}");
    }

    @Test
    void arrayOrderAndJsonTypesRemainSignificant() {
        different("[1,2]", "[2,1]");
        different("[1,1]", "[1]");
        different("1", "\"1\"");
        different("true", "\"true\"");
        different("true", "false");
        different("{}", "[]");
        different("null", "\"null\"");
        different("1", "[1]");
    }

    @Test
    void sqlNullJsonNullAndAbsentPropertyAreDistinct() {
        different(null, "null");
        different("{}", "{\"missing\":null}");
        different("{\"missing\":null}", "{\"missing\":\"null\"}");
    }

    @Test
    void exactDecimalsRetainEverySignificantDigit() {
        String exact = "12345678901234567890.12345678901234567890123456789";
        equivalent(exact, exact + "000");
        different(exact, "12345678901234567890.12345678901234567890123456788");
        String encoded = JsonbCanonicalizer.canonicalize(exact);
        assertTrue(encoded.endsWith(exact)); // Supplemental check, in addition to aggregate comparisons.
        equivalent("1e131071", "1.0e131071");
        equivalent("1e-16383", "1.00e-16383");
    }

    @Test
    void unicodeAndEscapesDecodeWithoutUnicodeNormalization() {
        equivalent("\"😀\"", "\"\\uD83D\\uDE00\"");
        equivalent("{\"é\":\"línea\\nβ\"}", "{\"\\u00e9\":\"l\\u00ednea\\nβ\"}");
        different("\"é\"", "\"e\\u0301\"");
        different("\"a:b\"", "\"ab:\"");
        equivalent("{\"\":1,\"😀\":true}", "{\"\\uD83D\\uDE00\":true,\"\":1.0}");
    }

    @Test
    void malformedOrPostgreSqlIncompatibleDocumentsAreRejected() {
        for (String invalid : List.of("", " ", "{", "{} []", "[1,]", "{\"a\":}", "NaN", "Infinity",
                "01", "\"\\u0000\"", "\"\\uD800\"", "\"\\uDC00\"", "1e2147483648")) {
            assertThrows(IllegalArgumentException.class, () -> JsonbCanonicalizer.canonicalize(invalid), invalid);
            assertThrows(IllegalArgumentException.class, () -> fingerprint(invalid), invalid);
        }
    }

    @Test
    void resourceLimitsRejectDepthSizeNumericRangeAndExponentAmplification() {
        for (String invalid : List.of(" ".repeat(JsonbCanonicalizer.MAX_DOCUMENT_CHARS + 1),
                "[".repeat(65) + "0" + "]".repeat(65), "1e131072", "1e-16384", "1e2000000000", "0e2000000000",
                "[" + "1e131071,".repeat(70) + "0]", "[" + "0,".repeat(100_000) + "0]")) {
            assertThrows(IllegalArgumentException.class, () -> JsonbCanonicalizer.canonicalize(invalid));
        }
        assertEquals(JsonbCanonicalizer.canonicalize("0"), JsonbCanonicalizer.canonicalize("0e100000"));
    }

    @Test
    void repeatedCanonicalizationAndFingerprintingAreDeterministic() {
        String document = "{\"b\":[100.000,\"x\",null],\"a\":true}";
        String canonical = JsonbCanonicalizer.canonicalize(document);
        String fingerprint = fingerprint(document);
        for (int i = 0; i < 10; i++) {
            assertEquals(canonical, JsonbCanonicalizer.canonicalize(document));
            assertEquals(fingerprint, fingerprint(document));
        }
        assertTrue(fingerprint.matches("m3-v2:[0-9a-f]{64}"));
    }

    @Test
    void onlyTheJsonbFieldIsSemanticallyNormalized() {
        var first = snapshot("{\"x\":1}");
        var second = snapshot("{\"x\":1.0}");
        assertEquals(CasoAggregateFingerprint.of(first), CasoAggregateFingerprint.of(second));
        var r = second.rubrica();
        var changedDescription = new CasoJpaAggregateSnapshot(second.caso(), second.escenarios(), second.preguntas(),
                second.opciones(), new RubricaRow(r.idRubrica(), r.idCaso(), r.nombre(), "{\"x\":1.0}", r.puntajeTotal(),
                        r.definicionEvaluacion(), r.fechaCreacion()), second.rubricaPreguntas());
        assertNotEquals(CasoAggregateFingerprint.of(first), CasoAggregateFingerprint.of(changedDescription),
                "JSON-looking ordinary text must remain literal");
        var equivalentDecimal = new CasoJpaAggregateSnapshot(first.caso(), first.escenarios(), first.preguntas(),
                first.opciones(), new RubricaRow(r.idRubrica(), r.idCaso(), r.nombre(), first.rubrica().descripcion(),
                        new BigDecimal("12.5000"), first.rubrica().definicionEvaluacion(), r.fechaCreacion()), Set.of());
        assertEquals(CasoAggregateFingerprint.of(first), CasoAggregateFingerprint.of(equivalentDecimal));
    }

    @Test
    void oldAndUnknownVersionsAreControlledConflictsRatherThanAcceptedAsV2() {
        for (String prefix : List.of("m3-v1:", "m3-v3:")) {
            var error = assertThrows(CasoDomainException.class,
                    () -> CasoCommandUpdateAdapter.validateFingerprintVersion(prefix + "0".repeat(64)));
            assertEquals(CasoCommandUpdateAdapter.STALE_CASE, error.code());
        }
        assertDoesNotThrow(() -> CasoCommandUpdateAdapter.validateFingerprintVersion(fingerprint("{}")));
        assertThrows(IllegalArgumentException.class, () -> CasoCommandUpdateAdapter.validateFingerprintVersion("not-a-token"));
    }

    private static void equivalent(String... documents) {
        String canonical = JsonbCanonicalizer.canonicalize(documents[0]);
        String fingerprint = fingerprint(documents[0]);
        for (String document : documents) {
            assertEquals(canonical, JsonbCanonicalizer.canonicalize(document));
            assertEquals(fingerprint, fingerprint(document));
        }
    }

    private static void different(String first, String second) {
        assertNotEquals(JsonbCanonicalizer.canonicalize(first), JsonbCanonicalizer.canonicalize(second));
        assertNotEquals(fingerprint(first), fingerprint(second));
    }

    private static String fingerprint(String json) { return CasoAggregateFingerprint.of(snapshot(json)); }

    private static CasoJpaAggregateSnapshot snapshot(String json) {
        return new CasoJpaAggregateSnapshot(new CasoRow(CASE, TEACHER, "Caso", null, null, 300, "Borrador", true,
                null, CREATED, null), List.of(), List.of(), List.of(),
                new RubricaRow(RUBRIC, CASE, "Rubrica", "{\"x\":1}", new BigDecimal("12.50"), json, CREATED), Set.of());
    }
}
