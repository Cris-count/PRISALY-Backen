package com.prisaly.m3.presentation.dto;

import com.prisaly.m3.application.update.CasoUpdateCommand.AsociacionRubrica;
import com.prisaly.m3.application.update.CasoUpdateCommand.GuardarRubricaCommand;
import com.prisaly.m3.application.update.JsonEstricto;
import com.prisaly.m3.domain.model.CriterioRubrica;
import com.prisaly.m3.domain.model.Rubrica;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** Rubric contract. Stored weights and scores are data only: no grading formula is applied here. */
public final class CasoRubricaDto {
    private CasoRubricaDto() { }

    public record CriterioDto(String id, String descripcion, int puntaje, BigDecimal ponderacion) { }
    public record AsociacionDto(String idPregunta, BigDecimal valorPregunta, String criterio) { }
    public record RubricaResponse(String idRubrica, String nombre, String descripcion, BigDecimal puntajeTotal,
            boolean completa, List<CriterioDto> criterios, List<AsociacionDto> preguntas) {
        public static RubricaResponse from(Rubrica r) {
            return new RubricaResponse(r.id(), r.nombre(), r.descripcion(), r.puntajeTotal(), r.isComplete(),
                    r.criterios().stream().map(c -> new CriterioDto(c.id(), c.descripcion(), c.puntaje(), c.ponderacion())).toList(),
                    r.preguntas().stream().sorted(Comparator.comparing(p -> p.idPregunta()))
                            .map(p -> new AsociacionDto(p.idPregunta(), p.valorPregunta(), p.criterio())).toList());
        }
    }

    public static GuardarRubricaCommand comando(String body) {
        Map<String, Object> raiz = campos(JsonEstricto.objeto(body), Set.of("nombre", "descripcion", "puntajeTotal", "criterios", "preguntas"));
        List<CriterioRubrica> criterios = new ArrayList<>();
        for (Object o : lista(raiz.get("criterios"))) {
            Map<String, Object> c = campos(objeto(o), Set.of("id", "descripcion", "puntaje", "ponderacion"));
            criterios.add(new CriterioRubrica(texto(c.get("id")), texto(c.get("descripcion")),
                    entero(c.get("puntaje")), decimal(c.get("ponderacion"))));
        }
        List<AsociacionRubrica> preguntas = new ArrayList<>();
        for (Object o : lista(raiz.get("preguntas"))) {
            Map<String, Object> p = campos(objeto(o), Set.of("idPregunta", "valorPregunta", "criterio"));
            preguntas.add(new AsociacionRubrica(uuid(texto(p.get("idPregunta"))), decimal(p.get("valorPregunta")), texto(p.get("criterio"))));
        }
        return new GuardarRubricaCommand(texto(raiz.get("nombre")), texto(raiz.get("descripcion")),
                decimal(raiz.get("puntajeTotal")), criterios, preguntas);
    }

    private static Map<String, Object> campos(Map<String, Object> mapa, Set<String> permitidos) {
        if (!permitidos.containsAll(mapa.keySet())) { throw invalido(); }
        return mapa;
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> objeto(Object valor) {
        if (!(valor instanceof Map<?, ?>)) { throw invalido(); }
        return (Map<String, Object>) valor;
    }

    private static List<?> lista(Object valor) {
        if (valor == null) { return List.of(); }
        if (!(valor instanceof List<?> l)) { throw invalido(); }
        return l;
    }

    private static String texto(Object valor) {
        if (valor != null && !(valor instanceof String)) { throw invalido(); }
        return (String) valor;
    }

    private static BigDecimal decimal(Object valor) {
        if (valor != null && !(valor instanceof BigDecimal)) { throw invalido(); }
        return (BigDecimal) valor;
    }

    private static int entero(Object valor) {
        if (!(valor instanceof BigDecimal n)) { throw invalido(); }
        try { return n.intValueExact(); }
        catch (ArithmeticException noEntero) { throw invalido(); }
    }

    private static UUID uuid(String valor) {
        if (valor == null) { throw invalido(); }
        UUID id = UUID.fromString(valor);
        if (!id.toString().equalsIgnoreCase(valor)) { throw invalido(); }
        return id;
    }

    private static IllegalArgumentException invalido() {
        return new IllegalArgumentException("Invalid rubric request");
    }
}
