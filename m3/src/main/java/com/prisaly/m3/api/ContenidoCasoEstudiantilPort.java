package com.prisaly.m3.api;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Proyección interna por lista permitida: nunca contiene corrección, rúbrica ni explicaciones.
 * El llamador debe acreditar titularidad y resolver el caso desde una programación M4.
 * Vacío si no está publicado/activo o contiene elementos no visibles/listos: no selecciona
 * un subconjunto académico ni decide elegibilidad. Requiere una transacción de lectura estable.
 */
public interface ContenidoCasoEstudiantilPort {
    Optional<Caso> consultar(UUID idCaso);

    record Caso(UUID idCaso, String titulo, String historia, String desarrolloSituacional,
                List<Escenario> escenarios) {
        public Caso { escenarios = List.copyOf(escenarios); }
    }
    record Escenario(UUID idEscenario, String titulo, String contexto, String instrucciones,
                     int orden, List<Pregunta> preguntas) {
        public Escenario { preguntas = List.copyOf(preguntas); }
    }
    record Pregunta(UUID idPregunta, String enunciado, int orden, List<Opcion> opciones) {
        public Pregunta { opciones = List.copyOf(opciones); }
    }
    record Opcion(UUID idOpcion, String texto, int orden) { }
}
