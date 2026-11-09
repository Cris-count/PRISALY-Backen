package com.prisaly.m3.presentation.dto;

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonToken;
import com.prisaly.m3.application.update.CasoUpdateCommand;
import com.prisaly.m3.application.update.CasoUpdateCommand.AgregarEscenarioCommand;
import com.prisaly.m3.application.update.CasoUpdateCommand.AgregarOpcionRespuestaCommand;
import com.prisaly.m3.application.update.CasoUpdateCommand.AgregarPreguntaCommand;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** Strict bodies for adding content: unknown or repeated fields and wrong JSON types are rejected. */
public final class CasoContenidoRequest {
    private static final String ESTADO_INICIAL_PREGUNTA = "Borrador";

    private CasoContenidoRequest() { }

    public static CasoUpdateCommand escenario(String body) {
        var f = parse(body, Set.of("idEscenario", "titulo", "contexto", "instrucciones", "orden"));
        return new AgregarEscenarioCommand(uuid(f, "idEscenario"), text(f, "titulo"), text(f, "contexto"),
                text(f, "instrucciones"), integer(f, "orden"));
    }

    public static CasoUpdateCommand pregunta(UUID idEscenario, String body) {
        var f = parse(body, Set.of("idPregunta", "enunciado", "textoAyuda", "orden"));
        return new AgregarPreguntaCommand(uuid(f, "idPregunta"), idEscenario, requiredText(f, "enunciado"),
                text(f, "textoAyuda"), ESTADO_INICIAL_PREGUNTA, true, integer(f, "orden"));
    }

    /** RF-07 and RF-08: correctness is explicit and every option carries its explanation. */
    public static CasoUpdateCommand opcion(UUID idPregunta, String body) {
        var f = parse(body, Set.of("idOpcion", "texto", "esCorrecta", "explicacion", "orden"));
        if (!(f.get("esCorrecta") instanceof Boolean correcta)) { throw invalid(); }
        return new AgregarOpcionRespuestaCommand(uuid(f, "idOpcion"), idPregunta, requiredText(f, "texto"),
                correcta, requiredText(f, "explicacion"), integer(f, "orden"));
    }

    private static Map<String, Object> parse(String body, Set<String> allowed) {
        try (var parser = new JsonFactory().createParser(body)) {
            if (parser.nextToken() != JsonToken.START_OBJECT) { throw invalid(); }
            Map<String, Object> values = new HashMap<>();
            while (parser.nextToken() != JsonToken.END_OBJECT) {
                if (parser.currentToken() != JsonToken.FIELD_NAME) { throw invalid(); }
                String key = parser.currentName();
                if (!allowed.contains(key) || values.containsKey(key)) { throw invalid(); }
                JsonToken token = parser.nextToken();
                Object value = switch (token) {
                    case VALUE_STRING -> validated(parser.getText());
                    case VALUE_NUMBER_INT -> parser.getIntValue();
                    case VALUE_TRUE -> Boolean.TRUE;
                    case VALUE_FALSE -> Boolean.FALSE;
                    case VALUE_NULL -> null;
                    default -> throw invalid();
                };
                values.put(key, value);
            }
            if (parser.nextToken() != null) { throw invalid(); }
            return values;
        } catch (IOException ex) { throw invalid(); }
    }

    private static String validated(String text) {
        if (text.indexOf('\u0000') >= 0) { throw invalid(); }
        return text;
    }

    private static UUID uuid(Map<String, Object> fields, String key) {
        if (!(fields.get(key) instanceof String value)) { throw invalid(); }
        UUID id = UUID.fromString(value);
        if (!id.toString().equalsIgnoreCase(value)) { throw invalid(); }
        return id;
    }

    private static String text(Map<String, Object> fields, String key) {
        Object value = fields.get(key);
        if (value != null && !(value instanceof String)) { throw invalid(); }
        return (String) value;
    }

    private static String requiredText(Map<String, Object> fields, String key) {
        String value = text(fields, key);
        if (value == null || value.isBlank()) { throw invalid(); }
        return value;
    }

    private static int integer(Map<String, Object> fields, String key) {
        if (!(fields.get(key) instanceof Integer value)) { throw invalid(); }
        return value;
    }

    private static IllegalArgumentException invalid() {
        return new IllegalArgumentException("Invalid case content request");
    }
}
