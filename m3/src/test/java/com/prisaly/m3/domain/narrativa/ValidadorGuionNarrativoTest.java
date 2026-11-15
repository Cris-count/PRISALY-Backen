package com.prisaly.m3.domain.narrativa;

import static org.junit.jupiter.api.Assertions.*;

import com.prisaly.m3.application.update.JsonEstricto;
import com.prisaly.m3.domain.narrativa.GuionNarrativoInvalidoException.Error;

import java.lang.reflect.RecordComponent;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.Test;

/** DC-BE-SIM-INCREMENTO-A-15: prisaly-guion-1 validation. Fixtures are test data, not approved content. */
class ValidadorGuionNarrativoTest {
    final ValidadorGuionNarrativo validador = new ValidadorGuionNarrativo(Set.of());

    GuionNarrativo validar(String json) {
        return validador.validar(JsonEstricto.objeto(json), json.getBytes(StandardCharsets.UTF_8).length);
    }

    List<Error> errores(String json) {
        return assertThrows(GuionNarrativoInvalidoException.class, () -> validar(json)).errores();
    }

    static String reemplazar(String de, String a) {
        assertTrue(GuionNarrativoPrueba.VALIDO.contains(de), de);
        return GuionNarrativoPrueba.VALIDO.replace(de, a);
    }

    @Test
    void unGuionValidoSeConvierteEnElModeloInmutable() {
        GuionNarrativo g = validar(GuionNarrativoPrueba.VALIDO);
        assertEquals("recepcion", g.inicio());
        assertEquals(2, g.espacios().size());
        assertEquals("sala", g.espacios().get(0).accesos().get(0).destino());
        assertEquals(GuionNarrativo.Expresion.SERENA, g.personajes().get(0).expresionInicial().orElseThrow());
        assertEquals(2, g.intervenciones().get(0).consecuencias().size());
        assertInstanceOf(GuionNarrativo.ConsecuenciaPersonaje.class, g.intervenciones().get(0).consecuencias().get(1));
        assertThrows(UnsupportedOperationException.class, () -> g.espacios().clear());
    }

    @Test
    void elModeloNoTieneNingunCampoAcademico() {
        for (Class<?> tipo : GuionNarrativo.class.getDeclaredClasses()) {
            if (!tipo.isRecord()) { continue; }
            for (RecordComponent c : tipo.getRecordComponents()) {
                assertFalse(ValidadorGuionNarrativo.CAMPOS_ACADEMICOS.contains(c.getName()), tipo + "." + c.getName());
            }
        }
        assertTrue(Arrays.stream(GuionNarrativo.class.getRecordComponents()).noneMatch(c -> c.getName().contains("pregunta")));
    }

    @Test
    void losCamposAcademicosSeRechazanExplicitamente() {
        var e = errores(reemplazar("\"texto\":\"Texto informativo de prueba.\"",
                "\"texto\":\"x\",\"idPregunta\":\"00000000-0000-0000-0000-000000000001\",\"esCorrecta\":true"));
        assertTrue(e.contains(new Error("espacios[0].informaciones[0].idPregunta", "CAMPO_ACADEMICO_PROHIBIDO")), e.toString());
        assertTrue(e.contains(new Error("espacios[0].informaciones[0].esCorrecta", "CAMPO_ACADEMICO_PROHIBIDO")), e.toString());
        var raiz = errores(reemplazar("\"inicio\":\"recepcion\",", "\"inicio\":\"recepcion\",\"preguntas\":[],\"otro\":1,"));
        assertTrue(raiz.contains(new Error("preguntas", "CAMPO_ACADEMICO_PROHIBIDO")), raiz.toString());
        assertTrue(raiz.contains(new Error("otro", "CAMPO_DESCONOCIDO")), raiz.toString());
    }

    @Test
    void referenciasYDuplicadosSeVerifican() {
        assertTrue(errores(reemplazar("\"inicio\":\"recepcion\"", "\"inicio\":\"azotea\""))
                .contains(new Error("inicio", "ESPACIO_INEXISTENTE")));
        assertTrue(errores(reemplazar("{\"destino\":\"sala\"", "{\"destino\":\"azotea\""))
                .contains(new Error("espacios[0].accesos[0].destino", "ESPACIO_INEXISTENTE")));
        assertTrue(errores(reemplazar("\"idEspacio\":\"recepcion\",\"idPersonaje\":\"persona-a\"",
                "\"idEspacio\":\"recepcion\",\"idPersonaje\":\"persona-z\""))
                .contains(new Error("intervenciones[0].idPersonaje", "PERSONAJE_INEXISTENTE")));
        assertTrue(errores(reemplazar("{\"idEspacio\":\"sala\"", "{\"idEspacio\":\"recepcion\""))
                .contains(new Error("espacios[1].idEspacio", "IDENTIFICADOR_DUPLICADO")));
        assertTrue(errores(reemplazar("\"idEspacio\":\"sala\"", "\"idEspacio\":\"Sala Grande\""))
                .contains(new Error("espacios[1].idEspacio", "IDENTIFICADOR_INVALIDO")));
    }

    @Test
    void soloTextoPlanoSinMarcadoNiControles() {
        assertTrue(errores(reemplazar("Texto informativo de prueba.", "<script>alert(1)</script>"))
                .contains(new Error("espacios[0].informaciones[0].texto", "MARCADO_NO_PERMITIDO")));
        assertTrue(errores(reemplazar("Texto informativo de prueba.", "a\\u0007b"))
                .contains(new Error("espacios[0].informaciones[0].texto", "CARACTER_DE_CONTROL")));
        assertDoesNotThrow(() -> validar(reemplazar("Texto informativo de prueba.", "Línea 1\\nLínea 2\\r\\n3 < 4 y 5 > 2")));
        assertTrue(errores(reemplazar("\"titulo\":\"Sala (prueba)\"", "\"titulo\":\"" + "x".repeat(201) + "\""))
                .contains(new Error("espacios[1].titulo", "TEXTO_DEMASIADO_LARGO")));
    }

    @Test
    void elCatalogoVisualVacioRechazaRecursosYUnoConfiguradoLosAcepta() {
        String conRecurso = reemplazar("\"titulo\":\"Sala (prueba)\"", "\"titulo\":\"Sala (prueba)\",\"recursoVisual\":\"sala-1\"");
        assertTrue(errores(conRecurso).contains(new Error("espacios[1].recursoVisual", "RECURSO_VISUAL_NO_CATALOGADO")));
        var conCatalogo = new ValidadorGuionNarrativo(Set.of("sala-1"));
        assertEquals("sala-1", conCatalogo.validar(JsonEstricto.objeto(conRecurso), 100).espacios().get(1).recursoVisual().orElseThrow());
    }

    @Test
    void formatoConsecuenciasYLimites() {
        assertTrue(errores(reemplazar("prisaly-guion-1", "prisaly-guion-2")).contains(new Error("formato", "FORMATO_NO_SOPORTADO")));
        assertTrue(errores(reemplazar("{\"tipo\":\"marca\"", "{\"tipo\":\"nota\""))
                .contains(new Error("intervenciones[0].consecuencias[0].tipo", "TIPO_DE_CONSECUENCIA_NO_SOPORTADO")));
        assertTrue(errores(reemplazar("\"expresion\":\"alegre\"", "\"expresion\":\"furiosa\""))
                .contains(new Error("intervenciones[0].consecuencias[1].expresion", "EXPRESION_NO_SOPORTADA")));
        assertTrue(errores(reemplazar("\"dialogos\":[{\"idPersonaje\":\"persona-a\",\"texto\":\"Hola, esto es una conversación de prueba.\"}]",
                "\"dialogos\":[]")).contains(new Error("intervenciones[0].dialogos", "DEMASIADO_POCOS_ELEMENTOS")));
        Map<String, Object> documento = JsonEstricto.objeto(GuionNarrativoPrueba.VALIDO);
        var grande = assertThrows(GuionNarrativoInvalidoException.class,
                () -> validador.validar(documento, ValidadorGuionNarrativo.MAX_BYTES + 1)).errores();
        assertTrue(grande.contains(new Error("", "DOCUMENTO_DEMASIADO_GRANDE")));
    }

    @Test
    void reuneTodosLosErroresEnUnaSolaRespuesta() {
        String varios = reemplazar("\"inicio\":\"recepcion\"", "\"inicio\":\"azotea\"").replace("prisaly-guion-1", "x");
        assertTrue(errores(varios).size() >= 2);
    }
}
