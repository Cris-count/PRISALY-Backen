package com.prisaly.m3.domain.narrativa;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Guion narrativo del caso, formato {@value #FORMATO} (DC-BE-SIM-INCREMENTO-A-15, candidato técnico).
 *
 * <p>Describe una exploración libre: espacios, la información de cada espacio, accesos entre espacios, personajes y
 * sus intervenciones (conversaciones informativas). <b>No contiene datos académicos</b>: ni preguntas, ni opciones,
 * ni claves correctas, ni vínculos con respuestas; una conversación nunca genera una respuesta académica. Las
 * consecuencias se limitan a los dos tipos del motor SIM-01 (marca booleana y estado/expresión de personaje).
 *
 * <p>Solo lo construye {@link ValidadorGuionNarrativo}; todas las listas son inmutables.
 */
public record GuionNarrativo(String inicio, List<Espacio> espacios, List<Personaje> personajes,
        List<Intervencion> intervenciones) {

    public static final String FORMATO = "prisaly-guion-1";

    public GuionNarrativo {
        Objects.requireNonNull(inicio, "inicio");
        espacios = List.copyOf(espacios);
        personajes = List.copyOf(personajes);
        intervenciones = List.copyOf(intervenciones);
    }

    public record Espacio(String idEspacio, String titulo, Optional<String> descripcion, Optional<String> recursoVisual,
            List<Informacion> informaciones, List<Acceso> accesos) {
        public Espacio {
            informaciones = List.copyOf(informaciones);
            accesos = List.copyOf(accesos);
        }
    }

    public record Informacion(String idInformacion, String texto) { }

    public record Acceso(String destino, String texto) { }

    public record Personaje(String idPersonaje, String nombre, Optional<String> rol, Optional<String> recursoVisual,
            Optional<String> estadoInicial, Optional<Expresion> expresionInicial) { }

    public record Intervencion(String idIntervencion, String idEspacio, String idPersonaje, List<Dialogo> dialogos,
            Optional<Condicion> condicion, List<Consecuencia> consecuencias) {
        public Intervencion {
            dialogos = List.copyOf(dialogos);
            consecuencias = List.copyOf(consecuencias);
        }
    }

    public record Dialogo(String idPersonaje, String texto) { }

    public record Condicion(String marca, boolean valor) { }

    /** Solo los dos tipos de consecuencia de SIM-01. */
    public sealed interface Consecuencia permits ConsecuenciaMarca, ConsecuenciaPersonaje { }

    public record ConsecuenciaMarca(String marca, boolean valor) implements Consecuencia { }

    public record ConsecuenciaPersonaje(String idPersonaje, String estado, Expresion expresion) implements Consecuencia { }

    /** Expresiones del motor SIM-01 ({@code narrativa.models.ts}). */
    public enum Expresion {
        SERENA("serena"), ATENTA("atenta"), ALEGRE("alegre");

        private final String valor;

        Expresion(String valor) { this.valor = valor; }

        public String valor() { return valor; }

        static Optional<Expresion> de(String valor) {
            for (Expresion e : values()) { if (e.valor.equals(valor)) { return Optional.of(e); } }
            return Optional.empty();
        }
    }
}
