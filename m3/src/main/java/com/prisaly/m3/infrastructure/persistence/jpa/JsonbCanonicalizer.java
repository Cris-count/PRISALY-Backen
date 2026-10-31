package com.prisaly.m3.infrastructure.persistence.jpa;

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.core.StreamReadConstraints;
import java.io.IOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * Local typed canonical encoding of PostgreSQL-compatible JSON values, not RFC 8785.
 * Jackson parses syntax; only semantic normalization and encoding are performed here.
 */
public final class JsonbCanonicalizer {
    static final int MAX_DOCUMENT_CHARS = 1_048_576;
    static final int MAX_NUMBER_CHARS = 150_000;
    static final int MAX_DEPTH = 64;
    static final int MAX_VALUES = 100_000;
    static final int MAX_CANONICAL_BYTES = 8_388_608;
    private static final JsonFactory FACTORY = JsonFactory.builder().streamReadConstraints(
            StreamReadConstraints.builder().maxNestingDepth(MAX_DEPTH).maxNumberLength(MAX_NUMBER_CHARS)
                    .maxStringLength(MAX_DOCUMENT_CHARS).maxNameLength(MAX_DOCUMENT_CHARS).build()).build();

    private JsonbCanonicalizer() { }

    /** Java null means SQL NULL; the JSON document "null" has a different encoding. */
    public static String canonicalize(String json) {
        if (json == null) { return "q"; }
        if (json.length() > MAX_DOCUMENT_CHARS) { throw rejected(); }
        try (JsonParser parser = FACTORY.createParser(json)) {
            Budget budget = new Budget();
            Object value = value(parser, parser.nextToken(), budget, 0);
            if (parser.nextToken() != null) { throw rejected(); }
            var output = new Output();
            encode(output, value);
            return output.text.toString();
        } catch (IOException | ArithmeticException failure) {
            throw new IllegalArgumentException("Malformed or resource-limited JSONB fingerprint document", failure);
        }
    }

    private static Object value(JsonParser parser, JsonToken token, Budget budget, int depth) throws IOException {
        if (token == null || depth > MAX_DEPTH || ++budget.values > MAX_VALUES) { throw rejected(); }
        return switch (token) {
            case VALUE_NULL -> JsonNull.VALUE;
            case VALUE_TRUE -> Boolean.TRUE;
            case VALUE_FALSE -> Boolean.FALSE;
            case VALUE_STRING -> checkedString(parser.getText());
            case VALUE_NUMBER_INT, VALUE_NUMBER_FLOAT -> number(parser.getText(), budget);
            case START_ARRAY -> {
                List<Object> array = new ArrayList<>();
                JsonToken next;
                while ((next = parser.nextToken()) != JsonToken.END_ARRAY) {
                    array.add(value(parser, next, budget, depth + 1));
                }
                yield array;
            }
            case START_OBJECT -> {
                Map<String, Object> object = new TreeMap<>();
                JsonToken next;
                while ((next = parser.nextToken()) != JsonToken.END_OBJECT) {
                    if (next != JsonToken.FIELD_NAME || ++budget.values > MAX_VALUES) { throw rejected(); }
                    String name = checkedString(parser.getText());
                    // PostgreSQL JSONB retains the last occurrence of a duplicate property.
                    object.put(name, value(parser, parser.nextToken(), budget, depth + 1));
                }
                yield object;
            }
            default -> throw rejected();
        };
    }

    private static Numeric number(String token, Budget budget) {
        try {
            // Jackson has already validated the token. No floating point accessors are used.
            BigDecimal decimal = new BigDecimal(token);
            if (Math.abs((long) decimal.scale()) > MAX_NUMBER_CHARS) { throw rejected(); }
            if (decimal.signum() == 0) {
                budget.numericCharacters(1);
                return new Numeric("0");
            }
            BigDecimal normalized = decimal.stripTrailingZeros();
            long scale = normalized.scale();
            long precision = normalized.precision();
            long integerDigits = precision - scale;
            // PostgreSQL 16 unconstrained numeric bounds, not an academic scoring scale.
            if (integerDigits > 131_072 || scale > 16_383) { throw rejected(); }
            long plainLength = scale <= 0 ? integerDigits
                    : scale < precision ? precision + 1 : scale + 2;
            if (normalized.signum() < 0) { plainLength++; }
            budget.numericCharacters(plainLength); // Bound exponent expansion BEFORE allocating it.
            return new Numeric(normalized.toPlainString());
        } catch (NumberFormatException | ArithmeticException failure) {
            throw new IllegalArgumentException("Unrepresentable JSONB number", failure);
        }
    }

    private static String checkedString(String text) {
        for (int i = 0; i < text.length(); i++) {
            char ch = text.charAt(i);
            if (ch == 0 || Character.isLowSurrogate(ch)) { throw rejected(); }
            if (Character.isHighSurrogate(ch)
                    && (++i >= text.length() || !Character.isLowSurrogate(text.charAt(i)))) { throw rejected(); }
        }
        return text; // No Unicode normalization, trimming or locale conversion.
    }

    private static void encode(Output out, Object value) {
        if (value == JsonNull.VALUE) { out.append("n"); }
        else if (value instanceof Boolean flag) { out.append(flag ? "t" : "f"); }
        else if (value instanceof String string) { out.scalar("s", string); }
        else if (value instanceof Numeric numeric) { out.scalar("d", numeric.text()); }
        else if (value instanceof List<?> array) {
            out.append("a" + array.size() + ":");
            for (Object item : array) { encode(out, item); }
        } else if (value instanceof Map<?, ?> object) {
            out.append("o" + object.size() + ":");
            for (var entry : object.entrySet()) {
                out.scalar("s", (String) entry.getKey());
                encode(out, entry.getValue());
            }
        } else { throw rejected(); }
    }

    private enum JsonNull { VALUE }
    private record Numeric(String text) { }

    private static final class Budget {
        private int values;
        private long expandedNumericCharacters;
        void numericCharacters(long length) {
            expandedNumericCharacters += length;
            if (length < 0 || expandedNumericCharacters > MAX_CANONICAL_BYTES) { throw rejected(); }
        }
    }

    private static final class Output {
        private final StringBuilder text = new StringBuilder();
        private long bytes;
        void append(String piece) {
            bytes += piece.getBytes(StandardCharsets.UTF_8).length;
            if (bytes > MAX_CANONICAL_BYTES) { throw rejected(); }
            text.append(piece);
        }
        void scalar(String type, String value) {
            append(type + value.getBytes(StandardCharsets.UTF_8).length + ":");
            append(value);
        }
    }

    private static IllegalArgumentException rejected() {
        return new IllegalArgumentException("Invalid, PostgreSQL-incompatible or resource-limited JSONB fingerprint document");
    }
}
