package com.prisaly.m3.domain.narrativa;

import com.prisaly.m3.domain.narrativa.GuionNarrativo.Acceso;
import com.prisaly.m3.domain.narrativa.GuionNarrativo.Condicion;
import com.prisaly.m3.domain.narrativa.GuionNarrativo.Consecuencia;
import com.prisaly.m3.domain.narrativa.GuionNarrativo.ConsecuenciaMarca;
import com.prisaly.m3.domain.narrativa.GuionNarrativo.ConsecuenciaPersonaje;
import com.prisaly.m3.domain.narrativa.GuionNarrativo.Dialogo;
import com.prisaly.m3.domain.narrativa.GuionNarrativo.Espacio;
import com.prisaly.m3.domain.narrativa.GuionNarrativo.Expresion;
import com.prisaly.m3.domain.narrativa.GuionNarrativo.Informacion;
import com.prisaly.m3.domain.narrativa.GuionNarrativo.Intervencion;
import com.prisaly.m3.domain.narrativa.GuionNarrativo.Personaje;
import com.prisaly.m3.domain.narrativa.GuionNarrativoInvalidoException.Error;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Validación pura del formato {@value GuionNarrativo#FORMATO} sobre el documento ya leído por un parser JSON estricto
 * (objetos {@code Map<String,Object>}, listas, {@code String}, {@code Boolean}, {@code BigDecimal}, {@code null}).
 * Reúne <b>todos</b> los errores y, si hay alguno, lanza {@link GuionNarrativoInvalidoException}.
 *
 * <ul>
 *   <li>Lista blanca de campos: un campo desconocido es un error; un campo académico ({@link #CAMPOS_ACADEMICOS}) se
 *       rechaza con un motivo explícito. El guion nunca transporta preguntas, opciones, claves ni respuestas.</li>
 *   <li>Identificadores locales {@code [a-z0-9][a-z0-9-]{0,63}}, únicos por tipo; referencias existentes.</li>
 *   <li>Solo texto plano: sin marcado ({@code <} seguido de letra, {@code /}, {@code !} o {@code ?}) ni caracteres de
 *       control salvo salto de línea y tabulador.</li>
 *   <li>{@code recursoVisual} debe pertenecer al catálogo suministrado. El catálogo productivo está vacío mientras
 *       D-SIM-7 no se apruebe: cualquier recurso visual se rechaza.</li>
 * </ul>
 */
public final class ValidadorGuionNarrativo {

    public static final int MAX_BYTES = 262_144;
    public static final int MAX_ESPACIOS = 50, MAX_PERSONAJES = 30, MAX_INTERVENCIONES = 300;
    static final int MAX_INFORMACIONES = 50, MAX_ACCESOS = 20, MAX_DIALOGOS = 50, MAX_CONSECUENCIAS = 20;
    public static final Set<String> CAMPOS_ACADEMICOS = Set.of("idPregunta", "idOpcion", "idOpcionSeleccionada",
            "pregunta", "preguntas", "opcion", "opciones", "esCorrecta", "respuesta", "respuestas", "explicacion",
            "nota", "calificacion", "peso", "valorPregunta", "puntaje", "rubrica", "idRubrica");
    private static final Pattern ID = Pattern.compile("[a-z0-9][a-z0-9-]{0,63}");
    private static final Pattern MARCADO = Pattern.compile("<[A-Za-z/!?]");

    private final Set<String> catalogoVisual;

    public ValidadorGuionNarrativo(Set<String> catalogoVisual) {
        this.catalogoVisual = Set.copyOf(Objects.requireNonNull(catalogoVisual, "catalogoVisual"));
    }

    /**
     * @param documento raíz ya leída de forma estricta
     * @param bytesUtf8 tamaño del documento recibido, para el límite de {@value #MAX_BYTES} bytes
     */
    public GuionNarrativo validar(Map<String, Object> documento, int bytesUtf8) {
        var v = new Recorrido();
        if (bytesUtf8 > MAX_BYTES) { v.error("", "DOCUMENTO_DEMASIADO_GRANDE"); }
        if (documento == null) { v.error("", "DOCUMENTO_REQUERIDO"); throw v.fallo(); }
        v.campos("", documento, Set.of("formato", "inicio", "espacios", "personajes", "intervenciones"));
        if (!GuionNarrativo.FORMATO.equals(documento.get("formato"))) { v.error("formato", "FORMATO_NO_SOPORTADO"); }

        List<Map<String, Object>> espaciosCrudos = v.lista("espacios", documento.get("espacios"), 1, MAX_ESPACIOS, true);
        List<Map<String, Object>> personajesCrudos = v.lista("personajes", documento.get("personajes"), 0, MAX_PERSONAJES, false);
        List<Map<String, Object>> intervencionesCrudas = v.lista("intervenciones", documento.get("intervenciones"), 0,
                MAX_INTERVENCIONES, false);

        Set<String> idsEspacio = new HashSet<>(), idsPersonaje = new HashSet<>(), idsIntervencion = new HashSet<>(),
                idsInformacion = new HashSet<>();
        for (int i = 0; i < espaciosCrudos.size(); i++) {
            v.idUnico("espacios[" + i + "].idEspacio", espaciosCrudos.get(i).get("idEspacio"), idsEspacio);
        }
        for (int i = 0; i < personajesCrudos.size(); i++) {
            v.idUnico("personajes[" + i + "].idPersonaje", personajesCrudos.get(i).get("idPersonaje"), idsPersonaje);
        }

        String inicio = v.texto("inicio", documento.get("inicio"), 64, true);
        if (inicio != null && !idsEspacio.contains(inicio)) { v.error("inicio", "ESPACIO_INEXISTENTE"); }

        List<Espacio> espacios = new ArrayList<>();
        for (int i = 0; i < espaciosCrudos.size(); i++) {
            String r = "espacios[" + i + "]";
            Map<String, Object> e = espaciosCrudos.get(i);
            v.campos(r, e, Set.of("idEspacio", "titulo", "descripcion", "recursoVisual", "informaciones", "accesos"));
            List<Informacion> informaciones = new ArrayList<>();
            List<Map<String, Object>> infos = v.lista(r + ".informaciones", e.get("informaciones"), 0, MAX_INFORMACIONES, false);
            for (int j = 0; j < infos.size(); j++) {
                String rj = r + ".informaciones[" + j + "]";
                v.campos(rj, infos.get(j), Set.of("idInformacion", "texto"));
                String id = v.idUnico(rj + ".idInformacion", infos.get(j).get("idInformacion"), idsInformacion);
                informaciones.add(new Informacion(id, v.texto(rj + ".texto", infos.get(j).get("texto"), 2000, true)));
            }
            List<Acceso> accesos = new ArrayList<>();
            List<Map<String, Object>> accs = v.lista(r + ".accesos", e.get("accesos"), 0, MAX_ACCESOS, false);
            for (int j = 0; j < accs.size(); j++) {
                String rj = r + ".accesos[" + j + "]";
                v.campos(rj, accs.get(j), Set.of("destino", "texto"));
                String destino = v.texto(rj + ".destino", accs.get(j).get("destino"), 64, true);
                if (destino != null && !idsEspacio.contains(destino)) { v.error(rj + ".destino", "ESPACIO_INEXISTENTE"); }
                accesos.add(new Acceso(destino, v.texto(rj + ".texto", accs.get(j).get("texto"), 200, true)));
            }
            espacios.add(new Espacio(texto(e.get("idEspacio")), v.texto(r + ".titulo", e.get("titulo"), 200, true),
                    Optional.ofNullable(v.texto(r + ".descripcion", e.get("descripcion"), 2000, false)),
                    v.recurso(r + ".recursoVisual", e.get("recursoVisual")), informaciones, accesos));
        }

        List<Personaje> personajes = new ArrayList<>();
        for (int i = 0; i < personajesCrudos.size(); i++) {
            String r = "personajes[" + i + "]";
            Map<String, Object> p = personajesCrudos.get(i);
            v.campos(r, p, Set.of("idPersonaje", "nombre", "rol", "recursoVisual", "estadoInicial", "expresionInicial"));
            personajes.add(new Personaje(texto(p.get("idPersonaje")), v.texto(r + ".nombre", p.get("nombre"), 100, true),
                    Optional.ofNullable(v.texto(r + ".rol", p.get("rol"), 200, false)),
                    v.recurso(r + ".recursoVisual", p.get("recursoVisual")),
                    Optional.ofNullable(v.texto(r + ".estadoInicial", p.get("estadoInicial"), 60, false)),
                    v.expresion(r + ".expresionInicial", p.get("expresionInicial"), false)));
        }

        List<Intervencion> intervenciones = new ArrayList<>();
        for (int i = 0; i < intervencionesCrudas.size(); i++) {
            String r = "intervenciones[" + i + "]";
            Map<String, Object> it = intervencionesCrudas.get(i);
            v.campos(r, it, Set.of("idIntervencion", "idEspacio", "idPersonaje", "dialogos", "condicion", "consecuencias"));
            String id = v.idUnico(r + ".idIntervencion", it.get("idIntervencion"), idsIntervencion);
            String espacio = v.referencia(r + ".idEspacio", it.get("idEspacio"), idsEspacio, "ESPACIO_INEXISTENTE");
            String personaje = v.referencia(r + ".idPersonaje", it.get("idPersonaje"), idsPersonaje, "PERSONAJE_INEXISTENTE");
            List<Dialogo> dialogos = new ArrayList<>();
            List<Map<String, Object>> dls = v.lista(r + ".dialogos", it.get("dialogos"), 1, MAX_DIALOGOS, true);
            for (int j = 0; j < dls.size(); j++) {
                String rj = r + ".dialogos[" + j + "]";
                v.campos(rj, dls.get(j), Set.of("idPersonaje", "texto"));
                dialogos.add(new Dialogo(v.referencia(rj + ".idPersonaje", dls.get(j).get("idPersonaje"), idsPersonaje,
                        "PERSONAJE_INEXISTENTE"), v.texto(rj + ".texto", dls.get(j).get("texto"), 1000, true)));
            }
            Optional<Condicion> condicion = Optional.empty();
            if (it.get("condicion") != null) {
                Map<String, Object> c = v.objeto(r + ".condicion", it.get("condicion"));
                if (c != null) {
                    v.campos(r + ".condicion", c, Set.of("marca", "valor"));
                    condicion = Optional.of(new Condicion(v.id(r + ".condicion.marca", c.get("marca")),
                            v.booleano(r + ".condicion.valor", c.get("valor"))));
                }
            }
            List<Consecuencia> consecuencias = new ArrayList<>();
            List<Map<String, Object>> cs = v.lista(r + ".consecuencias", it.get("consecuencias"), 0, MAX_CONSECUENCIAS, false);
            for (int j = 0; j < cs.size(); j++) {
                String rj = r + ".consecuencias[" + j + "]";
                Map<String, Object> c = cs.get(j);
                Object tipo = c.get("tipo");
                if ("marca".equals(tipo)) {
                    v.campos(rj, c, Set.of("tipo", "marca", "valor"));
                    consecuencias.add(new ConsecuenciaMarca(v.id(rj + ".marca", c.get("marca")), v.booleano(rj + ".valor", c.get("valor"))));
                } else if ("personaje".equals(tipo)) {
                    v.campos(rj, c, Set.of("tipo", "idPersonaje", "estado", "expresion"));
                    consecuencias.add(new ConsecuenciaPersonaje(
                            v.referencia(rj + ".idPersonaje", c.get("idPersonaje"), idsPersonaje, "PERSONAJE_INEXISTENTE"),
                            v.texto(rj + ".estado", c.get("estado"), 60, true),
                            v.expresion(rj + ".expresion", c.get("expresion"), true).orElse(null)));
                } else {
                    v.campos(rj, c, Set.of("tipo"));
                    v.error(rj + ".tipo", "TIPO_DE_CONSECUENCIA_NO_SOPORTADO");
                }
            }
            intervenciones.add(new Intervencion(id, espacio, personaje, dialogos, condicion, consecuencias));
        }
        if (!v.errores.isEmpty()) { throw v.fallo(); }
        return new GuionNarrativo(inicio, espacios, personajes, intervenciones);
    }

    private static String texto(Object valor) {
        return valor instanceof String s ? s : null;
    }

    /** Acumulador de errores del recorrido. */
    private final class Recorrido {
        final List<Error> errores = new ArrayList<>();

        void error(String ruta, String motivo) { errores.add(new Error(ruta, motivo)); }

        GuionNarrativoInvalidoException fallo() { return new GuionNarrativoInvalidoException(errores); }

        void campos(String ruta, Map<String, Object> objeto, Set<String> permitidos) {
            for (String clave : objeto.keySet()) {
                if (permitidos.contains(clave)) { continue; }
                String r = ruta.isEmpty() ? clave : ruta + "." + clave;
                error(r, CAMPOS_ACADEMICOS.contains(clave) ? "CAMPO_ACADEMICO_PROHIBIDO" : "CAMPO_DESCONOCIDO");
            }
        }

        @SuppressWarnings("unchecked")
        Map<String, Object> objeto(String ruta, Object valor) {
            if (valor instanceof Map<?, ?> m) { return (Map<String, Object>) m; }
            error(ruta, "OBJETO_REQUERIDO");
            return null;
        }

        List<Map<String, Object>> lista(String ruta, Object valor, int minimo, int maximo, boolean requerida) {
            List<Map<String, Object>> salida = new ArrayList<>();
            if (valor == null) {
                if (requerida || minimo > 0) { error(ruta, "LISTA_REQUERIDA"); }
                return salida;
            }
            if (!(valor instanceof List<?> lista)) { error(ruta, "LISTA_REQUERIDA"); return salida; }
            if (lista.size() < minimo) { error(ruta, "DEMASIADO_POCOS_ELEMENTOS"); }
            if (lista.size() > maximo) { error(ruta, "DEMASIADOS_ELEMENTOS"); return salida; }
            for (int i = 0; i < lista.size(); i++) {
                Map<String, Object> m = objeto(ruta + "[" + i + "]", lista.get(i));
                if (m != null) { salida.add(m); }
            }
            return salida;
        }

        String texto(String ruta, Object valor, int maximo, boolean requerido) {
            if (valor == null) {
                if (requerido) { error(ruta, "TEXTO_REQUERIDO"); }
                return null;
            }
            if (!(valor instanceof String s)) { error(ruta, "TEXTO_REQUERIDO"); return null; }
            if (requerido && s.isBlank()) { error(ruta, "TEXTO_REQUERIDO"); }
            if (s.codePointCount(0, s.length()) > maximo) { error(ruta, "TEXTO_DEMASIADO_LARGO"); }
            if (MARCADO.matcher(s).find()) { error(ruta, "MARCADO_NO_PERMITIDO"); }
            if (s.chars().anyMatch(ch -> (ch < 0x20 && ch != '\n' && ch != '\r' && ch != '\t') || ch == 0x7f)) {
                error(ruta, "CARACTER_DE_CONTROL");
            }
            return s;
        }

        String id(String ruta, Object valor) {
            if (!(valor instanceof String s) || !ID.matcher(s).matches()) { error(ruta, "IDENTIFICADOR_INVALIDO"); return null; }
            return s;
        }

        String idUnico(String ruta, Object valor, Set<String> vistos) {
            String id = id(ruta, valor);
            if (id != null && !vistos.add(id)) { error(ruta, "IDENTIFICADOR_DUPLICADO"); }
            return id;
        }

        String referencia(String ruta, Object valor, Set<String> existentes, String motivo) {
            String id = id(ruta, valor);
            if (id != null && !existentes.contains(id)) { error(ruta, motivo); }
            return id;
        }

        boolean booleano(String ruta, Object valor) {
            if (valor instanceof Boolean b) { return b; }
            error(ruta, "BOOLEANO_REQUERIDO");
            return false;
        }

        Optional<String> recurso(String ruta, Object valor) {
            if (valor == null) { return Optional.empty(); }
            if (!(valor instanceof String s) || !catalogoVisual.contains(s)) {
                error(ruta, "RECURSO_VISUAL_NO_CATALOGADO");
                return Optional.empty();
            }
            return Optional.of(s);
        }

        Optional<Expresion> expresion(String ruta, Object valor, boolean requerida) {
            if (valor == null) {
                if (requerida) { error(ruta, "EXPRESION_REQUERIDA"); }
                return Optional.empty();
            }
            Optional<Expresion> e = valor instanceof String s ? Expresion.de(s) : Optional.empty();
            if (e.isEmpty()) { error(ruta, "EXPRESION_NO_SOPORTADA"); }
            return e;
        }
    }
}
