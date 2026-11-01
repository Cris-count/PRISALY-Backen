package com.prisaly.m3.infrastructure.persistence.jpa;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.Comparator;
import java.util.Base64;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;

/** Versioned canonical binary encoding, not record.toString() or delimiter joining. */
final class CasoAggregateFingerprint {
    static final String PREFIX = "m3-v2:";
    private CasoAggregateFingerprint() { }

    static String of(CasoJpaAggregateSnapshot snapshot) {
        try {
            var bytes = new ByteArrayOutputStream();
            try (var out = new DataOutputStream(bytes)) {
                write(out, "M3-INC-10B.1:v2");
                write(out, snapshot.caso());
                rows(out, snapshot.escenarios().stream().sorted(Comparator.comparing(
                        CasoJpaAggregateSnapshot.EscenarioRow::idEscenario)).toList());
                rows(out, snapshot.preguntas().stream().sorted(Comparator.comparing(
                        CasoJpaAggregateSnapshot.PreguntaRow::idPregunta)).toList());
                rows(out, snapshot.opciones().stream().sorted(Comparator.comparing(
                        CasoJpaAggregateSnapshot.OpcionRespuestaRow::idOpcion)).toList());
                write(out, snapshot.rubrica());
                rows(out, snapshot.rubricaPreguntas().stream().sorted(Comparator.comparing(
                        CasoJpaAggregateSnapshot.RubricaPreguntaRow::idRubricaPregunta)).toList());
            }
            // Preserve the aggregate's typed, length-delimited composition. Its Base64
            // representation is unambiguous UTF-8 text for the version-2 digest input.
            byte[] utf8 = Base64.getEncoder().encodeToString(bytes.toByteArray()).getBytes(StandardCharsets.UTF_8);
            return PREFIX + HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(utf8));
        } catch (IOException | ReflectiveOperationException | NoSuchAlgorithmException failure) {
            throw new IllegalStateException("Cannot fingerprint the complete aggregate", failure);
        }
    }

    private static void rows(DataOutputStream out, List<? extends Record> rows)
            throws IOException, ReflectiveOperationException {
        out.writeInt(rows.size());
        for (Record row : rows) { write(out, row); }
    }

    private static void write(DataOutputStream out, Object value)
            throws IOException, ReflectiveOperationException {
        if (value == null) { out.writeByte(0); }
        else if (value instanceof String text) {
            out.writeByte(1);
            byte[] utf8 = text.getBytes(StandardCharsets.UTF_8);
            out.writeInt(utf8.length);
            out.write(utf8);
        } else if (value instanceof UUID uuid) {
            out.writeByte(2); out.writeLong(uuid.getMostSignificantBits()); out.writeLong(uuid.getLeastSignificantBits());
        } else if (value instanceof Integer number) {
            out.writeByte(3); out.writeInt(number);
        } else if (value instanceof Boolean flag) {
            out.writeByte(4); out.writeBoolean(flag);
        } else if (value instanceof BigDecimal decimal) {
            out.writeByte(5); write(out, decimal.stripTrailingZeros().toPlainString());
        } else if (value instanceof Instant instant) {
            out.writeByte(6); out.writeLong(instant.getEpochSecond()); out.writeInt(instant.getNano());
        } else if (value instanceof Record record) {
            out.writeByte(7);
            write(out, record.getClass().getSimpleName());
            var components = record.getClass().getRecordComponents();
            out.writeInt(components.length);
            for (var component : components) {
                write(out, component.getName());
                Object field = component.getAccessor().invoke(record);
                if (record instanceof CasoJpaAggregateSnapshot.RubricaRow
                        && component.getName().equals("definicionEvaluacion")) {
                    out.writeByte(8); // Explicit JSONB type; all other String fields remain literal text.
                    write(out, JsonbCanonicalizer.canonicalize((String) field));
                } else {
                    write(out, field);
                }
            }
        } else {
            throw new IllegalArgumentException("Unsupported fingerprint value: " + value.getClass());
        }
    }
}
