package com.prisaly.m5.application.investigacion;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/**
 * CB-02B (A1): lo que la investigación puede referenciar del guion <b>fijado en el intento</b> (la versión de
 * {@code "FaseIntento"."idGuion"}, nunca la última publicada). Solo identificadores del guion prisaly-guion-1: los
 * nombres libres de edificios o pistas no son identificadores académicos (S1).
 *
 * <p>Se construye a partir del documento ya validado por M3 al guardarse y servido por M5 con su huella verificada
 * ({@link com.prisaly.m5.application.fase.NarrativaIntento}); aquí solo se leen identificadores.
 */
public record GuionFijado(UUID idGuion, String hashDefinicion, Set<String> espacios, Map<String, String> espacioDeInformacion,
        Map<String, Intervencion> intervenciones, Set<String> marcas) {

    public record Intervencion(String idEspacio, String idPersonaje) {
        public Intervencion {
            Objects.requireNonNull(idEspacio, "idEspacio");
            Objects.requireNonNull(idPersonaje, "idPersonaje");
        }
    }

    public GuionFijado {
        Objects.requireNonNull(idGuion, "idGuion");
        if (hashDefinicion == null || !hashDefinicion.matches("[0-9a-f]{64}")) {
            throw new IllegalArgumentException("hashDefinicion");
        }
        espacios = Set.copyOf(espacios);
        espacioDeInformacion = Map.copyOf(espacioDeInformacion);
        intervenciones = Map.copyOf(intervenciones);
        marcas = Set.copyOf(marcas);
    }

    /** Lee los identificadores de un documento prisaly-guion-1 ya decodificado (objetos como {@code Map}, listas como {@code List}). */
    public static GuionFijado desde(UUID idGuion, String hashDefinicion, Map<String, Object> documento) {
        if (documento == null || !"prisaly-guion-1".equals(documento.get("formato"))) {
            throw new IllegalArgumentException("El guion fijado no es prisaly-guion-1");
        }
        Set<String> espacios = new HashSet<>(), marcas = new HashSet<>();
        Map<String, String> informaciones = new HashMap<>();
        Map<String, Intervencion> intervenciones = new HashMap<>();
        for (Map<String, Object> e : objetos(documento.get("espacios"))) {
            String id = texto(e.get("idEspacio"));
            espacios.add(id);
            for (Map<String, Object> i : objetos(e.get("informaciones"))) {
                informaciones.put(texto(i.get("idInformacion")), id);
            }
        }
        for (Map<String, Object> it : objetos(documento.get("intervenciones"))) {
            intervenciones.put(texto(it.get("idIntervencion")), new Intervencion(texto(it.get("idEspacio")), texto(it.get("idPersonaje"))));
            if (it.get("condicion") instanceof Map<?, ?> c && c.get("marca") instanceof String m) {
                marcas.add(m);
            }
            for (Map<String, Object> c : objetos(it.get("consecuencias"))) {
                if ("marca".equals(c.get("tipo"))) {
                    marcas.add(texto(c.get("marca")));
                }
            }
        }
        return new GuionFijado(idGuion, hashDefinicion, espacios, informaciones, intervenciones, marcas);
    }

    @SuppressWarnings("unchecked")
    private static List<Map<String, Object>> objetos(Object valor) {
        if (valor == null) {
            return List.of();
        }
        if (!(valor instanceof List<?> lista)) {
            throw new IllegalArgumentException("Se esperaba una lista en el guion fijado");
        }
        return (List<Map<String, Object>>) lista;
    }

    private static String texto(Object valor) {
        if (!(valor instanceof String s) || s.isBlank()) {
            throw new IllegalArgumentException("Identificador ausente en el guion fijado");
        }
        return s;
    }
}
