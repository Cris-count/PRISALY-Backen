package com.prisaly.m3.infrastructure.config;

import com.prisaly.m3.application.update.JsonEstricto;
import com.prisaly.m3.domain.narrativa.CatalogoVisual;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Lee el catálogo visual canónico desde el classpath (DC-CB-03 §3). El hash es el SHA-256 de los bytes exactos del
 * recurso: Frontend y Unreal verifican sus copias contra ese valor. Lectura estricta: un campo desconocido o un tipo
 * incorrecto impide arrancar con un catálogo ambiguo.
 */
public final class CatalogoVisualRecurso {
    public static final String VIGENTE = "prisaly/catalogo-visual/catalogo-visual-1.json";

    private CatalogoVisualRecurso() { }

    public static CatalogoVisual cargarVigente() {
        return cargar(VIGENTE);
    }

    public static CatalogoVisual cargar(String recurso) {
        try (InputStream in = CatalogoVisualRecurso.class.getClassLoader().getResourceAsStream(recurso)) {
            if (in == null) { throw new IllegalStateException("No existe el catalogo visual " + recurso); }
            return leer(in.readAllBytes());
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    public static CatalogoVisual leer(byte[] bytes) {
        Map<String, Object> raiz = JsonEstricto.objeto(new String(bytes, StandardCharsets.UTF_8));
        campos(raiz, Set.of("version", "capacidadMaximaPorEdificio", "comun", "edificios", "puestos"), "raiz");
        Map<String, CatalogoVisual.Edificio> edificios = new LinkedHashMap<>();
        for (Map<String, Object> e : lista(raiz.get("edificios"), "edificios")) {
            campos(e, Set.of("clave", "nombre", "zoneId", "puertaId"), "edificio");
            var ed = new CatalogoVisual.Edificio(texto(e, "clave"), texto(e, "nombre"), texto(e, "zoneId"), texto(e, "puertaId"));
            if (edificios.put(ed.clave(), ed) != null) { throw new IllegalStateException("Edificio duplicado " + ed.clave()); }
        }
        Map<String, CatalogoVisual.Puesto> puestos = new LinkedHashMap<>();
        for (Map<String, Object> p : lista(raiz.get("puestos"), "puestos")) {
            campos(p, Set.of("clave", "edificio", "npcId", "modelo"), "puesto");
            var pu = new CatalogoVisual.Puesto(texto(p, "clave"), texto(p, "edificio"), texto(p, "npcId"), texto(p, "modelo"));
            if (puestos.put(pu.clave(), pu) != null) { throw new IllegalStateException("Puesto duplicado " + pu.clave()); }
        }
        if (!(raiz.get("capacidadMaximaPorEdificio") instanceof BigDecimal capacidad)) {
            throw new IllegalStateException("capacidadMaximaPorEdificio requerida");
        }
        return new CatalogoVisual(texto(raiz, "version"), sha256(bytes), capacidad.intValueExact(), edificios, puestos);
    }

    static String sha256(byte[] bytes) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    private static void campos(Map<String, Object> objeto, Set<String> permitidos, String ruta) {
        for (String clave : objeto.keySet()) {
            if (!permitidos.contains(clave)) { throw new IllegalStateException("Campo desconocido en " + ruta + ": " + clave); }
        }
    }

    @SuppressWarnings("unchecked")
    private static List<Map<String, Object>> lista(Object valor, String ruta) {
        if (!(valor instanceof List<?> l)) { throw new IllegalStateException(ruta + " debe ser una lista"); }
        for (Object o : l) { if (!(o instanceof Map<?, ?>)) { throw new IllegalStateException(ruta + " contiene un no-objeto"); } }
        return (List<Map<String, Object>>) valor;
    }

    private static String texto(Map<String, Object> objeto, String campo) {
        if (!(objeto.get(campo) instanceof String s) || s.isBlank()) { throw new IllegalStateException(campo + " requerido"); }
        return s;
    }
}
