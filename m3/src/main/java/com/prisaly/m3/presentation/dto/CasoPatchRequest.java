package com.prisaly.m3.presentation.dto;

import com.fasterxml.jackson.core.*;
import com.prisaly.m3.domain.model.Caso;
import java.util.*;
import java.io.IOException;

/** Sparse root fields only. Omission preserves a field; explicit null clears nullable text. */
public record CasoPatchRequest(Map<String, Object> fields) {
    public CasoPatchRequest {
        fields = Collections.unmodifiableMap(new HashMap<>(fields));
        if (fields.isEmpty() || !Set.of("titulo", "historia", "desarrolloSituacional", "tiempoEstimadoSegundos")
                .containsAll(fields.keySet())) { throw new IllegalArgumentException("Invalid patch fields"); }
    }

    public static CasoPatchRequest fromJson(String body) {
        try (var parser = new JsonFactory().createParser(body)) {
            if (parser.nextToken() != JsonToken.START_OBJECT) { throw invalid(); }
            Map<String, Object> values = new HashMap<>();
            while (parser.nextToken() != JsonToken.END_OBJECT) {
                if (parser.currentToken() != JsonToken.FIELD_NAME) { throw invalid(); }
                String key = parser.currentName();
                if (values.containsKey(key)) { throw invalid(); }
                JsonToken token = parser.nextToken();
                Object value;
                if (key.equals("tiempoEstimadoSegundos")) {
                    if (token != JsonToken.VALUE_NUMBER_INT) { throw invalid(); }
                    value = parser.getIntValue();
                } else if (Set.of("titulo", "historia", "desarrolloSituacional").contains(key)) {
                    if (token == JsonToken.VALUE_NULL && !key.equals("titulo")) { value = null; }
                    else if (token == JsonToken.VALUE_STRING) { value = parser.getText(); }
                    else { throw invalid(); }
                    if (value instanceof String text) { validateText(text); }
                } else { throw invalid(); }
                values.put(key, value);
            }
            if (parser.nextToken() != null) { throw invalid(); }
            return new CasoPatchRequest(values);
        } catch (IOException ex) { throw invalid(); }
    }

    private static void validateText(String text) {
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c == 0 || Character.isLowSurrogate(c)) { throw invalid(); }
            if (Character.isHighSurrogate(c) && (++i == text.length() || !Character.isLowSurrogate(text.charAt(i)))) {
                throw invalid();
            }
        }
    }

    public Caso apply(Caso stored) {
        return new Caso(stored.id(), (String) fields.getOrDefault("titulo", stored.titulo()), stored.docenteId(),
                (Integer) fields.getOrDefault("tiempoEstimadoSegundos", stored.tiempoEstimadoSegundos()),
                stored.estado(), stored.escenarios(), stored.rubrica(), stored.contenidoEvaluableBloqueado(),
                (String) fields.getOrDefault("historia", stored.historia()),
                (String) fields.getOrDefault("desarrolloSituacional", stored.desarrolloSituacional()),
                stored.activo(), stored.fechaCreacion(), stored.fechaActualizacion(), stored.fechaBloqueoAcademico());
    }

    private static IllegalArgumentException invalid() { return new IllegalArgumentException("Invalid case patch"); }
}
