package com.prisaly.m3.domain.narrativa;

import static org.junit.jupiter.api.Assertions.*;

import com.prisaly.m3.application.update.JsonEstricto;
import com.prisaly.m3.domain.narrativa.GuionNarrativoInvalidoException.Error;
import com.prisaly.m3.infrastructure.config.CatalogoVisualRecurso;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

/** DC-CB-03 §4 (CB-02): reglas del catálogo visual canónico sobre prisaly-guion-1. Casos A y B de DC-CB-ING-MASTER-01 §22. */
class ValidadorGuionNarrativoCatalogoTest {
    static final CatalogoVisual CATALOGO = CatalogoVisualRecurso.cargarVigente();
    final ValidadorGuionNarrativo validador = new ValidadorGuionNarrativo(CATALOGO);

    static String recurso(String nombre) {
        try (InputStream in = ValidadorGuionNarrativoCatalogoTest.class.getClassLoader().getResourceAsStream("case-builder/" + nombre)) {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    static final String CASO_A = recurso("guion-caso-a.json");
    static final String CASO_B = recurso("guion-caso-b.json");

    GuionNarrativo validar(String json) {
        return validador.validar(JsonEstricto.objeto(json), json.getBytes(StandardCharsets.UTF_8).length);
    }

    List<Error> errores(String json) {
        return assertThrows(GuionNarrativoInvalidoException.class, () -> validar(json)).errores();
    }

    static String en(String base, String de, String a) {
        assertTrue(base.contains(de), de);
        return base.replace(de, a);
    }

    @Test
    void elCatalogoVigenteTieneTresEdificiosYCuatroPuestos() {
        assertEquals("catalogo-visual-1", CATALOGO.version());
        assertTrue(CATALOGO.hash().matches("[0-9a-f]{64}"));
        // Hash publicado a Front y Unreal (CB-03). Si cambia por fin de linea (CRLF) o por edicion, Unreal rechaza la
        // configuracion con CATALOGO_INCOMPATIBLE: una edicion intencionada exige nueva version y actualizar las copias.
        assertEquals("bfa39caac8aaf2afb9c1177ac8024de14cac6a47a320f49e8b4813579ec7ccfe", CATALOGO.hash());
        assertEquals(3, CATALOGO.edificios().size());
        assertEquals(4, CATALOGO.puestos().size());
        assertEquals(2, CATALOGO.capacidadEfectiva("edificio:hospital"));
        assertEquals(1, CATALOGO.capacidadEfectiva("edificio:comisaria"));
        assertEquals(1, CATALOGO.capacidadEfectiva("edificio:fiscalia"));
        assertEquals("hospital_entrada", CATALOGO.edificio("edificio:hospital").orElseThrow().zoneId());
        assertEquals("hosp-npc-doctor", CATALOGO.puesto("npc:hosp-npc-doctor").orElseThrow().npcId());
    }

    @Test
    void elHashEsElDeLosBytesExactosDelRecurso() {
        byte[] bytes = recursoPrincipal();
        assertEquals(CATALOGO.hash(), CatalogoVisualRecurso.leer(bytes).hash());
        byte[] alterado = new String(bytes, StandardCharsets.UTF_8).replace("Hospital", "Hospital ").getBytes(StandardCharsets.UTF_8);
        assertNotEquals(CATALOGO.hash(), CatalogoVisualRecurso.leer(alterado).hash());
    }

    @Test
    void losCasosAyBSonValidosConElMismoMedicoVisual() {
        GuionNarrativo a = validar(CASO_A), b = validar(CASO_B);
        assertEquals("npc:hosp-npc-doctor", a.personajes().get(0).recursoVisual().orElseThrow());
        assertEquals("npc:hosp-npc-doctor", b.personajes().get(0).recursoVisual().orElseThrow());
        assertNotEquals(a.personajes().get(0).nombre(), b.personajes().get(0).nombre());
        assertEquals(3, a.intervenciones().get(0).dialogos().size(), "saludo, principal y despedida");
    }

    @Test
    void unPersonajeNoPuedeHablarFueraDeSuEdificio() {
        String json = en(CASO_A, "\"idIntervencion\":\"oficial-conversacion\",\"idEspacio\":\"comisaria\"",
                "\"idIntervencion\":\"oficial-conversacion\",\"idEspacio\":\"hospital\"");
        assertTrue(errores(json).contains(new Error("intervenciones[1].idPersonaje", "PERSONAJE_FUERA_DE_SU_EDIFICIO")));
        String dialogoAjeno = en(CASO_A, "{\"idPersonaje\":\"oficial-rios\",\"texto\":\"Recibimos",
                "{\"idPersonaje\":\"dr-martinez\",\"texto\":\"Recibimos");
        assertTrue(errores(dialogoAjeno).contains(new Error("intervenciones[1].dialogos[0].idPersonaje", "PERSONAJE_FUERA_DE_SU_EDIFICIO")));
    }

    @Test
    void unPuestoFisicoNoSeRepiteNiSuperaLaCapacidad() {
        // Comisaría tiene un solo puesto: un segundo personaje sobre el mismo puesto repite y además excede la capacidad.
        String json = en(CASO_A, "\"recursoVisual\":\"npc:pol-npc-oficial\"}]",
                "\"recursoVisual\":\"npc:pol-npc-oficial\"},"
                        + "{\"idPersonaje\":\"cabo-paz\",\"nombre\":\"Cabo Paz\",\"recursoVisual\":\"npc:pol-npc-oficial\"}]");
        var e = errores(json);
        assertTrue(e.contains(new Error("personajes[2].recursoVisual", "PUESTO_DUPLICADO")), e.toString());
        assertTrue(e.contains(new Error("personajes[2].recursoVisual", "CAPACIDAD_EXCEDIDA")), e.toString());
        // En el Hospital (capacidad 2) repetir el médico es un puesto duplicado, no una capacidad excedida.
        var hospital = errores(en(CASO_A, "\"recursoVisual\":\"npc:pol-npc-oficial\"", "\"recursoVisual\":\"npc:hosp-npc-doctor\""));
        assertTrue(hospital.contains(new Error("personajes[1].recursoVisual", "PUESTO_DUPLICADO")), hospital.toString());
        assertFalse(hospital.contains(new Error("personajes[1].recursoVisual", "CAPACIDAD_EXCEDIDA")), hospital.toString());
    }

    @Test
    void elPersonajeExigeQueSuEdificioEsteSeleccionado() {
        String json = en(CASO_A, "\"recursoVisual\":\"npc:pol-npc-oficial\"", "\"recursoVisual\":\"npc:fis-npc-fiscal\"");
        assertTrue(errores(json).contains(new Error("personajes[1].recursoVisual", "EDIFICIO_NO_SELECCIONADO")));
    }

    @Test
    void cadaRecursoDebeSerDelTipoCorrectoYNoRepetirEdificio() {
        var tipos = errores(en(en(CASO_A, "\"recursoVisual\":\"edificio:comisaria\"", "\"recursoVisual\":\"npc:pol-npc-oficial\""),
                "\"recursoVisual\":\"npc:hosp-npc-doctor\"", "\"recursoVisual\":\"edificio:hospital\""));
        assertTrue(tipos.contains(new Error("espacios[1].recursoVisual", "RECURSO_VISUAL_TIPO_INCORRECTO")), tipos.toString());
        assertTrue(tipos.contains(new Error("personajes[0].recursoVisual", "RECURSO_VISUAL_TIPO_INCORRECTO")), tipos.toString());
        var duplicado = errores(en(CASO_A, "\"recursoVisual\":\"edificio:comisaria\"", "\"recursoVisual\":\"edificio:hospital\""));
        assertTrue(duplicado.contains(new Error("espacios[1].recursoVisual", "EDIFICIO_DUPLICADO")), duplicado.toString());
    }

    @Test
    void unRecursoFueraDelCatalogoSigueRechazandose() {
        assertTrue(errores(en(CASO_A, "npc:pol-npc-oficial", "npc:pol-npc-sargento"))
                .contains(new Error("personajes[1].recursoVisual", "RECURSO_VISUAL_NO_CATALOGADO")));
    }

    @Test
    void unEspacioSinRecursoVisualNoAlojaPersonajesConPuesto() {
        String json = en(CASO_A, "\"titulo\":\"Comisaría\",\"recursoVisual\":\"edificio:comisaria\"", "\"titulo\":\"Comisaría\"");
        var e = errores(json);
        assertTrue(e.contains(new Error("personajes[1].recursoVisual", "EDIFICIO_NO_SELECCIONADO")), e.toString());
        assertTrue(e.contains(new Error("intervenciones[1].idPersonaje", "PERSONAJE_FUERA_DE_SU_EDIFICIO")), e.toString());
    }

    @Test
    void elCatalogoRechazaDefinicionesIncoherentes() {
        var hospital = new CatalogoVisual.Edificio("edificio:hospital", "Hospital", "hospital_entrada", "hosp-puerta");
        String hash = "0".repeat(64);
        assertThrows(IllegalArgumentException.class, () -> new CatalogoVisual("catalogo-visual-1", hash, 3, Map.of(),
                Map.of("npc:x", new CatalogoVisual.Puesto("npc:x", "edificio:hospital", "x", "medico"))));
        assertThrows(IllegalArgumentException.class, () -> new CatalogoVisual("catalogo-visual-1", hash, 1,
                Map.of("edificio:hospital", hospital),
                Map.of("npc:a", new CatalogoVisual.Puesto("npc:a", "edificio:hospital", "a", "medico"),
                        "npc:b", new CatalogoVisual.Puesto("npc:b", "edificio:hospital", "b", "medico"))));
        assertThrows(IllegalStateException.class, () -> CatalogoVisualRecurso.leer(
                new String(recursoPrincipal(), StandardCharsets.UTF_8).replace("\"comun\"", "\"extra\":1,\"comun\"")
                        .getBytes(StandardCharsets.UTF_8)));
    }

    static byte[] recursoPrincipal() {
        try (InputStream in = CatalogoVisualRecurso.class.getClassLoader().getResourceAsStream(CatalogoVisualRecurso.VIGENTE)) {
            return in.readAllBytes();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
