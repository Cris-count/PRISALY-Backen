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
        Map<String, String> valores = new HashMap<>();
        valores.put("sufijo", UUID.randomUUID().toString().substring(0, 8));
        valores.put("rolDocente", String.valueOf(ThreadLocalRandom.current().nextInt(100_000, 1_000_000_000)));
        valores.put("rolEstudiante", String.valueOf(ThreadLocalRandom.current().nextInt(100_000, 1_000_000_000)));
        Matcher marcador = MARCADOR.matcher(plantilla());
        StringBuilder sql = new StringBuilder();
        while (marcador.find()) {
            String valor = valores.computeIfAbsent(marcador.group(1), clave -> UUID.randomUUID().toString());
            marcador.appendReplacement(sql, Matcher.quoteReplacement(valor));
        }
        marcador.appendTail(sql);
        jdbc.execute(sql.toString());
        return new CadenaIntentoPrueba(valores);
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

    UUID convocatoria() {
        return UUID.fromString(valores.get("convocatoria"));
    }

    UUID sesion() {
        return UUID.fromString(valores.get("sesion"));
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

    private static String plantilla() {
        try (InputStream entrada = CadenaIntentoPrueba.class.getResourceAsStream("/db/m5-cadena-intento.sql")) {
            if (entrada == null) {
                throw new IllegalStateException("No se encontró db/m5-cadena-intento.sql");
            }
            return new String(entrada.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }
}
