package com.prisaly.m3.application.update;

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import java.io.IOException;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Strict JSON reader: one root object, no repeated keys, exact decimals, no trailing content. */
public final class JsonEstricto {
    private static final int PROFUNDIDAD_MAXIMA = 16;

    private JsonEstricto() { }

    public static Map<String, Object> objeto(String json) {
        try (var parser = new JsonFactory().createParser(json)) {
            if (parser.nextToken() != JsonToken.START_OBJECT) { throw invalido(); }
            Map<String, Object> raiz = leerObjeto(parser, 0);
            if (parser.nextToken() != null) { throw invalido(); }
            return raiz;
        } catch (IOException ex) { throw invalido(); }
    }

    private static Map<String, Object> leerObjeto(JsonParser parser, int nivel) throws IOException {
        if (nivel > PROFUNDIDAD_MAXIMA) { throw invalido(); }
        Map<String, Object> valores = new HashMap<>();
        while (parser.nextToken() != JsonToken.END_OBJECT) {
            String clave = parser.currentName();
            if (valores.containsKey(clave)) { throw invalido(); }
            valores.put(clave, leerValor(parser, parser.nextToken(), nivel));
        }
        return valores;
    }

    private static Object leerValor(JsonParser parser, JsonToken token, int nivel) throws IOException {
        return switch (token) {
            case START_OBJECT -> leerObjeto(parser, nivel + 1);
            case START_ARRAY -> {
                if (nivel > PROFUNDIDAD_MAXIMA) { throw invalido(); }
                List<Object> lista = new ArrayList<>();
                for (JsonToken t = parser.nextToken(); t != JsonToken.END_ARRAY; t = parser.nextToken()) {
                    lista.add(leerValor(parser, t, nivel + 1));
                }
                yield lista;
            }
            case VALUE_STRING -> {
                String texto = parser.getText();
                if (texto.indexOf('\u0000') >= 0) { throw invalido(); }
                yield texto;
            }
            case VALUE_NUMBER_INT, VALUE_NUMBER_FLOAT -> new BigDecimal(parser.getText());
            case VALUE_TRUE -> Boolean.TRUE;
            case VALUE_FALSE -> Boolean.FALSE;
            case VALUE_NULL -> null;
            default -> throw invalido();
        };
    }

    private static IllegalArgumentException invalido() {
        return new IllegalArgumentException("Invalid JSON document");
    }
}
