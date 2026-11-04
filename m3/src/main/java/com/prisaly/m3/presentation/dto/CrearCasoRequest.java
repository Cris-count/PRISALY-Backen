package com.prisaly.m3.presentation.dto;

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import com.prisaly.m3.domain.builder.CasoBuilder;
import java.io.IOException;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/** Minimal draft only. No owner, audit metadata or academic children are accepted. */
public record CrearCasoRequest(UUID idCaso, String titulo, Integer tiempoEstimadoSegundos,
        String historia, String desarrolloSituacional) {
    private static final JsonFactory JSON = new JsonFactory();

    public CrearCasoRequest {
        if (idCaso == null || titulo == null || titulo.isBlank()
                || titulo.codePointCount(0, titulo.length()) > 150
                || tiempoEstimadoSegundos == null || tiempoEstimadoSegundos <= 0) {
            throw new InvalidCasoRequestException();
        }
        requirePostgresText(titulo);
        requirePostgresText(historia);
        requirePostgresText(desarrolloSituacional);
    }

    private static void requirePostgresText(String value) {
        if (value == null) { return; }
        for (int i = 0; i < value.length(); i++) {
            char character = value.charAt(i);
            if (character == 0 || Character.isLowSurrogate(character)) {
                throw new InvalidCasoRequestException();
            }
            if (Character.isHighSurrogate(character)) {
                if (++i == value.length() || !Character.isLowSurrogate(value.charAt(i))) {
                    throw new InvalidCasoRequestException();
                }
            }
        }
    }

    /**
     * Jackson streaming enforces exact JSON types locally, independently of app's
     * global coercion/unknown-property settings. This is not a JSONB academic parser.
     */
    public static CrearCasoRequest fromJson(String body) {
        if (body == null) { throw new InvalidCasoRequestException(); }
        try (JsonParser parser = JSON.createParser(body)) {
            if (parser.nextToken() != JsonToken.START_OBJECT) { throw new InvalidCasoRequestException(); }
            UUID id = null;
            String title = null, history = null, development = null;
            Integer seconds = null;
            Set<String> seen = new HashSet<>();
            while (parser.nextToken() != JsonToken.END_OBJECT) {
                if (parser.currentToken() != JsonToken.FIELD_NAME) { throw new InvalidCasoRequestException(); }
                String field = parser.currentName();
                if (!seen.add(field)) { throw new InvalidCasoRequestException(); }
                parser.nextToken();
                switch (field) {
                    case "idCaso" -> {
                        String value = text(parser);
                        if (value == null) { throw new InvalidCasoRequestException(); }
                        id = UUID.fromString(value);
                        if (!id.toString().equalsIgnoreCase(value)) { throw new InvalidCasoRequestException(); }
                    }
                    case "titulo" -> title = text(parser);
                    case "historia" -> history = text(parser);
                    case "desarrolloSituacional" -> development = text(parser);
                    case "tiempoEstimadoSegundos" -> {
                        if (parser.currentToken() != JsonToken.VALUE_NUMBER_INT) { throw new InvalidCasoRequestException(); }
                        seconds = parser.getIntValue();
                    }
                    default -> throw new InvalidCasoRequestException();
                }
            }
            if (parser.nextToken() != null) { throw new InvalidCasoRequestException(); }
            return new CrearCasoRequest(id, title, seconds, history, development);
        } catch (IOException | IllegalArgumentException ex) {
            throw new InvalidCasoRequestException();
        }
    }

    private static String text(JsonParser parser) throws IOException {
        if (parser.currentToken() == JsonToken.VALUE_NULL) { return null; }
        if (parser.currentToken() != JsonToken.VALUE_STRING) { throw new InvalidCasoRequestException(); }
        return parser.getText();
    }

    public CasoBuilder toBuilder(UUID authenticatedOwner) {
        return new CasoBuilder().withId(idCaso.toString())
                .withDocenteId(java.util.Objects.requireNonNull(authenticatedOwner).toString())
                .withTitulo(titulo).withTiempoEstimadoSegundos(tiempoEstimadoSegundos)
                .withHistoria(historia).withDesarrolloSituacional(desarrolloSituacional);
    }
}
