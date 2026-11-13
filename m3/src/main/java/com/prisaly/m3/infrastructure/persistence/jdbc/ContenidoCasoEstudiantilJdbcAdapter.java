package com.prisaly.m3.infrastructure.persistence.jdbc;

import com.prisaly.m3.api.ContenidoCasoEstudiantilPort;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/** Solo tablas propias M3. Cuatro lecturas en el snapshot del coordinador, sin N+1. */
@Repository
public class ContenidoCasoEstudiantilJdbcAdapter implements ContenidoCasoEstudiantilPort {
    private final JdbcTemplate jdbc;
    public ContenidoCasoEstudiantilJdbcAdapter(JdbcTemplate jdbc) { this.jdbc = jdbc; }
    private record Cabecera(UUID id, String titulo, String historia, String desarrollo) { }
    private record E(UUID id, String titulo, String contexto, String instrucciones, int orden, boolean activo) { }
    private record P(UUID id, UUID escenario, String enunciado, int orden, boolean visible, String estado) { }
    private record O(UUID id, UUID pregunta, String texto, int orden) { }

    @Override
    @Transactional(propagation = Propagation.MANDATORY, readOnly = true)
    public Optional<Caso> consultar(UUID idCaso) {
        var casos = jdbc.query("""
                SELECT "idCaso", "nombre", "historia", "desarrolloSituacional"
                FROM public."Caso" WHERE "idCaso"=? AND "estado"='Publicado' AND "activo"=true
                """, (rs,n) -> new Cabecera(rs.getObject(1, UUID.class), rs.getString(2), rs.getString(3), rs.getString(4)), idCaso);
        if (casos.isEmpty()) return Optional.empty();
        var escenarios = jdbc.query("""
                SELECT "idEscenario", "titulo", "contexto", "instrucciones", "orden", "activo"
                FROM public."Escenario" WHERE "idCaso"=? ORDER BY "orden", "idEscenario"
                """, (rs,n) -> new E(rs.getObject(1, UUID.class), rs.getString(2), rs.getString(3), rs.getString(4), rs.getInt(5), rs.getBoolean(6)), idCaso);
        var preguntas = jdbc.query("""
                SELECT p."idPregunta", p."idEscenario", p."enunciado", p."orden", p."visible", p."estadoEdicion"
                FROM public."Pregunta" p JOIN public."Escenario" e ON e."idEscenario"=p."idEscenario"
                WHERE e."idCaso"=? ORDER BY e."orden", p."orden", p."idPregunta"
                """, (rs,n) -> new P(rs.getObject(1, UUID.class), rs.getObject(2, UUID.class), rs.getString(3), rs.getInt(4), rs.getBoolean(5), rs.getString(6)), idCaso);
        // No inferir qué subconjunto integra un recorrido: rechazar completo antes de entregar ocultos.
        if (escenarios.stream().anyMatch(e -> !e.activo()) || preguntas.stream().anyMatch(p -> !p.visible() || !"Lista".equals(p.estado()))) {
            return Optional.empty();
        }
        var opciones = jdbc.query("""
                SELECT o."idOpcion", o."idPregunta", o."texto", o."orden"
                FROM public."OpcionRespuesta" o JOIN public."Pregunta" p ON p."idPregunta"=o."idPregunta"
                JOIN public."Escenario" e ON e."idEscenario"=p."idEscenario"
                WHERE e."idCaso"=? ORDER BY o."orden", o."idOpcion"
                """, (rs,n) -> new O(rs.getObject(1, UUID.class), rs.getObject(2, UUID.class), rs.getString(3), rs.getInt(4)), idCaso);
        var c = casos.get(0);
        List<Escenario> resultado = escenarios.stream().map(e -> new Escenario(e.id(), e.titulo(), e.contexto(), e.instrucciones(), e.orden(),
                preguntas.stream().filter(p -> p.escenario().equals(e.id())).map(p -> new Pregunta(p.id(), p.enunciado(), p.orden(),
                        opciones.stream().filter(o -> o.pregunta().equals(p.id())).map(o -> new Opcion(o.id(), o.texto(), o.orden())).toList())).toList())).toList();
        return Optional.of(new Caso(c.id(), c.titulo(), c.historia(), c.desarrollo(), resultado));
    }
}
