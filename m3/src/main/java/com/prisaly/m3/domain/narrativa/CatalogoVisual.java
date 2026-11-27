package com.prisaly.m3.domain.narrativa;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.regex.Pattern;

/**
 * Catálogo visual canónico del Case Builder (D-SIM-7, D-CB-1, D-CB-2; DC-CB-03 §3). Describe lo que existe físicamente
 * en el simulador: edificios (zona y puerta de Unreal) y puestos de personaje (NPC de Unreal) con su edificio.
 *
 * <p>Propietario: M3. El recurso versionado vive en {@code prisaly/catalogo-visual/}; Frontend y Unreal usan copias
 * verificadas por {@link #hash()}. Los guiones lo referencian con {@code recursoVisual} = clave {@code edificio:*} o
 * {@code npc:*}. Plaza Central es común y no se selecciona.
 */
public record CatalogoVisual(String version, String hash, int capacidadMaximaPorEdificio,
        Map<String, Edificio> edificios, Map<String, Puesto> puestos) {

    public static final String PREFIJO_EDIFICIO = "edificio:";
    public static final String PREFIJO_PUESTO = "npc:";
    private static final Pattern VERSION = Pattern.compile("catalogo-visual-[1-9][0-9]{0,3}");
    private static final Pattern HASH = Pattern.compile("[0-9a-f]{64}");
    private static final Pattern CLAVE = Pattern.compile("(edificio|npc):[a-z0-9][a-z0-9-]{0,63}");

    public record Edificio(String clave, String nombre, String zoneId, String puertaId) { }

    public record Puesto(String clave, String edificio, String npcId, String modelo) { }

    public CatalogoVisual {
        Objects.requireNonNull(version, "version");
        Objects.requireNonNull(hash, "hash");
        if (!VERSION.matcher(version).matches()) { throw new IllegalArgumentException("version de catalogo invalida: " + version); }
        if (!HASH.matcher(hash).matches()) { throw new IllegalArgumentException("hash de catalogo invalido"); }
        if (capacidadMaximaPorEdificio < 1) { throw new IllegalArgumentException("capacidadMaximaPorEdificio"); }
        edificios = Map.copyOf(edificios);
        puestos = Map.copyOf(puestos);
        for (var e : edificios.entrySet()) {
            if (!e.getKey().equals(e.getValue().clave()) || !e.getKey().startsWith(PREFIJO_EDIFICIO)
                    || !CLAVE.matcher(e.getKey()).matches()) {
                throw new IllegalArgumentException("clave de edificio invalida: " + e.getKey());
            }
        }
        Map<String, Integer> porEdificio = new LinkedHashMap<>();
        for (var p : puestos.entrySet()) {
            if (!p.getKey().equals(p.getValue().clave()) || !p.getKey().startsWith(PREFIJO_PUESTO)
                    || !CLAVE.matcher(p.getKey()).matches()) {
                throw new IllegalArgumentException("clave de puesto invalida: " + p.getKey());
            }
            if (!edificios.containsKey(p.getValue().edificio())) {
                throw new IllegalArgumentException("el puesto " + p.getKey() + " apunta a un edificio inexistente");
            }
            porEdificio.merge(p.getValue().edificio(), 1, Integer::sum);
        }
        for (var c : porEdificio.entrySet()) {
            if (c.getValue() > capacidadMaximaPorEdificio) {
                throw new IllegalArgumentException("el edificio " + c.getKey() + " supera la capacidad maxima");
            }
        }
    }

    public Optional<Edificio> edificio(String clave) { return Optional.ofNullable(edificios.get(clave)); }

    public Optional<Puesto> puesto(String clave) { return Optional.ofNullable(puestos.get(clave)); }

    /** Capacidad efectiva: el menor valor entre el máximo del MVP y los puestos que existen físicamente. */
    public int capacidadEfectiva(String claveEdificio) {
        long existentes = puestos.values().stream().filter(p -> p.edificio().equals(claveEdificio)).count();
        return (int) Math.min(capacidadMaximaPorEdificio, existentes);
    }

    /** Claves válidas para {@code recursoVisual} (compatibilidad con el catálogo plano anterior). */
    public List<String> claves() {
        return java.util.stream.Stream.concat(edificios.keySet().stream(), puestos.keySet().stream()).sorted().toList();
    }
}
