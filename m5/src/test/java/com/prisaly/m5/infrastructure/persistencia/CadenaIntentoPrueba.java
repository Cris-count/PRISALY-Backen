package com.prisaly.m5.infrastructure.persistencia;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.springframework.jdbc.core.JdbcTemplate;

import com.prisaly.m5.application.ReferenciaConvocatoria;
import com.prisaly.m5.application.ReferenciaIntento;
import com.prisaly.m5.domain.respuesta.ReferenciaPregunta;
import com.prisaly.m5.domain.respuesta.ValorRespuesta;

/**
 * Inserta, con identificadores nuevos, la cadena mínima de BD-005.2 que
 * permite un Intento EnCurso (plantilla {@code db/m5-cadena-intento.sql}).
 * Cada prueba usa su propia cadena, de modo que no comparten datos.
 */
final class CadenaIntentoPrueba {

    private static final Pattern MARCADOR = Pattern.compile("\\$\\{(\\w+)}");

    private final Map<String, String> valores;

    private CadenaIntentoPrueba(Map<String, String> valores) {
        this.valores = valores;
    }

    static CadenaIntentoPrueba crear(JdbcTemplate jdbc) {
        return crear(jdbc, "/db/m5-cadena-intento.sql");
    }

    /** La misma cadena con una Rubrica del caso creada antes de programar la sesión (evaluación, DC-BE-24H-01). */
    static CadenaIntentoPrueba crearConRubrica(JdbcTemplate jdbc) {
        return crear(jdbc, "/db/m5-cadena-intento-con-rubrica.sql");
    }

    /** La misma cadena hasta la Convocatoria, sin Participacion ni Intento: para el primer inicio. */
    static CadenaIntentoPrueba crearHastaConvocatoria(JdbcTemplate jdbc) {
        return crear(jdbc, "/db/m5-cadena-convocatoria.sql");
    }

    private static CadenaIntentoPrueba crear(JdbcTemplate jdbc, String recurso) {
        Map<String, String> valores = new HashMap<>();
        valores.put("sufijo", UUID.randomUUID().toString().substring(0, 8));
        valores.put("rolDocente", String.valueOf(ThreadLocalRandom.current().nextInt(100_000, 1_000_000_000)));
        valores.put("rolEstudiante", String.valueOf(ThreadLocalRandom.current().nextInt(100_000, 1_000_000_000)));
        Matcher marcador = MARCADOR.matcher(plantilla(recurso));
        StringBuilder sql = new StringBuilder();
        while (marcador.find()) {
            String valor = valores.computeIfAbsent(marcador.group(1), clave -> UUID.randomUUID().toString());
            marcador.appendReplacement(sql, Matcher.quoteReplacement(valor));
        }
        marcador.appendTail(sql);
        jdbc.execute(sql.toString());
        return new CadenaIntentoPrueba(valores);
    }

    UUID rubrica() {
        return UUID.fromString(valores.get("rubrica"));
    }

    ReferenciaIntento intento() {
        return new ReferenciaIntento(valores.get("intento"));
    }

    UUID idIntento() {
        return UUID.fromString(valores.get("intento"));
    }

    UUID estudiante() {
        return UUID.fromString(valores.get("estudiante"));
    }

    ReferenciaConvocatoria referenciaConvocatoria() {
        return new ReferenciaConvocatoria(valores.get("convocatoria"));
    }

    UUID convocatoria() {
        return UUID.fromString(valores.get("convocatoria"));
    }

    UUID sesion() {
        return UUID.fromString(valores.get("sesion"));
    }

    UUID caso() {
        return UUID.fromString(valores.get("caso"));
    }

    UUID docente() {
        return UUID.fromString(valores.get("docente"));
    }

    ReferenciaPregunta pregunta1() {
        return new ReferenciaPregunta(valores.get("pregunta1"));
    }

    ReferenciaPregunta pregunta2() {
        return new ReferenciaPregunta(valores.get("pregunta2"));
    }

    ValorRespuesta opcion1a() {
        return new ValorRespuesta(valores.get("opcion1a"));
    }

    ValorRespuesta opcion1b() {
        return new ValorRespuesta(valores.get("opcion1b"));
    }

    ValorRespuesta opcion1c() {
        return new ValorRespuesta(valores.get("opcion1c"));
    }

    ValorRespuesta opcion2a() {
        return new ValorRespuesta(valores.get("opcion2a"));
    }

    private static String plantilla(String recurso) {
        try (InputStream entrada = CadenaIntentoPrueba.class.getResourceAsStream(recurso)) {
            if (entrada == null) {
                throw new IllegalStateException("No se encontró " + recurso);
            }
            return new String(entrada.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }
}
