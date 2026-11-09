package com.prisaly.m3.application.update;

import com.fasterxml.jackson.core.JsonFactory;
import com.prisaly.m3.domain.model.CriterioRubrica;
import java.io.IOException;
import java.io.StringWriter;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * PD-06: versioned criteria inside Rubrica.definicionEvaluacion, without new tables. Any document
 * that is not a valid v1 yields no criteria, so the rubric stays incomplete and cannot be published.
 */
public final class RubricaDefinicionCodec {
    public static final int VERSION = 1;

    private RubricaDefinicionCodec() { }

    public static String escribir(List<CriterioRubrica> criterios) {
        try (var salida = new StringWriter(); var json = new JsonFactory().createGenerator(salida)) {
            json.writeStartObject();
            json.writeNumberField("version", VERSION);
            json.writeArrayFieldStart("criterios");
            for (CriterioRubrica criterio : criterios) {
                json.writeStartObject();
                json.writeStringField("id", criterio.id());
                json.writeStringField("descripcion", criterio.descripcion());
                json.writeNumberField("puntaje", criterio.puntaje());
                if (criterio.ponderacion() != null) { json.writeNumberField("ponderacion", criterio.ponderacion()); }
                json.writeEndObject();
            }
            json.writeEndArray();
            json.writeEndObject();
            json.flush();
            return salida.toString();
        } catch (IOException ex) {
            throw new IllegalStateException("Rubric definition could not be written", ex);
        }
    }

    public static List<CriterioRubrica> leer(String documento) {
        if (documento == null) { return List.of(); }
        try {
            Map<String, Object> raiz = JsonEstricto.objeto(documento);
            if (!(raiz.get("version") instanceof BigDecimal v) || v.compareTo(BigDecimal.valueOf(VERSION)) != 0
                    || !(raiz.get("criterios") instanceof List<?> lista)) {
                return List.of();
            }
            List<CriterioRubrica> criterios = new ArrayList<>();
            for (Object elemento : lista) {
                Map<?, ?> c = (Map<?, ?>) elemento;
                criterios.add(new CriterioRubrica((String) c.get("id"), (String) c.get("descripcion"),
                        ((BigDecimal) c.get("puntaje")).intValueExact(), (BigDecimal) c.get("ponderacion")));
            }
            return List.copyOf(criterios);
        } catch (RuntimeException documentoNoReconocido) {
            return List.of();
        }
    }
}
