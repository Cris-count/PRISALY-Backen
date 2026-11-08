package com.prisaly.m4.infrastructure.config;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.List;
import java.util.Properties;
import java.util.Set;
import java.util.TreeSet;
import org.springframework.jdbc.core.JdbcTemplate;

/** Read-only, versioned schema contracts. Selecting DD02 is NOT ratification or student enablement. */
public final class M4SchemaContract {
    private M4SchemaContract() { }
    public static final String HISTORICO = "bd0052-v009";
    public static final String DD02 = "bd0052-dd02-v012";
    public static final Set<String> TABLAS_HISTORICAS = Set.of(
            "Rol", "Permiso", "Usuario", "UsuarioPermiso", "Estudiante", "Caso", "Escenario",
            "Pregunta", "OpcionRespuesta", "Sesion", "Convocatoria", "Participacion", "Intento",
            "Respuesta", "AutorizacionRepeticion", "Rubrica", "RubricaPregunta", "Calificacion",
            "RetroalimentacionGeneral", "Aviso", "EnvioCorreo");

    public static void verificar(JdbcTemplate jdbc, String contrato) {
        if (!HISTORICO.equals(contrato) && !DD02.equals(contrato)) throw pendiente("contrato desconocido");
        Set<String> esperadas = new TreeSet<>(TABLAS_HISTORICAS);
        if (DD02.equals(contrato)) esperadas.addAll(Set.of("ActivacionCuentaEstudiante", "SesionEstudiante"));
        Set<String> presentes = new TreeSet<>(jdbc.queryForList("""
                SELECT table_name FROM information_schema.tables
                WHERE table_schema='public' AND table_type='BASE TABLE'
                  AND table_name<>'flyway_schema_history'
                """, String.class));
        var faltantes = new TreeSet<>(esperadas); faltantes.removeAll(presentes);
        var adicionales = new TreeSet<>(presentes); adicionales.removeAll(esperadas);
        if (!faltantes.isEmpty() || !adicionales.isEmpty()) {
            throw pendiente("identidad incompatible; faltantes=" + faltantes + "; adicionales=" + adicionales);
        }
        var referencia = referencia(contrato);
        if (!referencia.getProperty("structure-sha256").equals(huella(estructura(jdbc)))) {
            throw pendiente("estructura incompatible con " + contrato + "; revisar columnas, claves, indices y triggers");
        }
        if (!referencia.getProperty("history-sha256").equals(huella(historial(jdbc)))) {
            throw pendiente("historial Flyway incompatible con " + contrato + "; conciliar sin repair ni borrar filas");
        }
    }

    private static Properties referencia(String contrato) {
        try (var stream = M4SchemaContract.class.getResourceAsStream("/m4-schema/" + contrato + ".properties")) {
            if (stream == null) throw pendiente("contrato no disponible");
            var p = new Properties(); p.load(new InputStreamReader(stream, StandardCharsets.UTF_8));
            if (!p.getProperty("structure-sha256", "").matches("[a-f0-9]{64}")
                    || !p.getProperty("history-sha256", "").matches("[a-f0-9]{64}")) throw pendiente("referencia invalida");
            return p;
        } catch (java.io.IOException ex) { throw new IllegalStateException("M4-ESQUEMA-PENDIENTE: lectura de contrato", ex); }
    }

    // No OIDs, row data, owners, installation times or current sequence values enter the fingerprint.
    public static List<String> estructura(JdbcTemplate jdbc) {
        return jdbc.queryForList("""
                SELECT 'relation|'||c.relname||'|'||c.relkind::text||'|'||c.relrowsecurity||'|'||c.relforcerowsecurity AS definition
                FROM pg_class c JOIN pg_namespace n ON n.oid=c.relnamespace
                WHERE n.nspname='public' AND c.relkind IN ('r','p','v','m','f') AND c.relname<>'flyway_schema_history'
                UNION ALL
                SELECT 'column|'||c.relname||'|'||a.attnum||'|'||a.attname||'|'||format_type(a.atttypid,a.atttypmod)
                    ||'|'||a.attnotnull||'|'||a.attidentity::text||'|'||a.attgenerated::text||'|'||coalesce(pg_get_expr(d.adbin,d.adrelid),'')
                FROM pg_attribute a JOIN pg_class c ON c.oid=a.attrelid JOIN pg_namespace n ON n.oid=c.relnamespace
                LEFT JOIN pg_attrdef d ON d.adrelid=c.oid AND d.adnum=a.attnum
                WHERE n.nspname='public' AND c.relkind IN ('r','p','v','m','f') AND c.relname<>'flyway_schema_history'
                    AND a.attnum>0 AND NOT a.attisdropped
                UNION ALL
                SELECT 'constraint|'||c.relname||'|'||k.conname||'|'||k.convalidated||'|'||pg_get_constraintdef(k.oid,false)
                FROM pg_constraint k JOIN pg_class c ON c.oid=k.conrelid JOIN pg_namespace n ON n.oid=c.relnamespace
                WHERE n.nspname='public' AND c.relname<>'flyway_schema_history'
                UNION ALL
                SELECT 'index|'||c.relname||'|'||i.indisvalid||'|'||i.indisready||'|'||pg_get_indexdef(i.indexrelid)
                FROM pg_index i JOIN pg_class c ON c.oid=i.indrelid JOIN pg_namespace n ON n.oid=c.relnamespace
                WHERE n.nspname='public' AND c.relname<>'flyway_schema_history'
                UNION ALL
                SELECT 'trigger|'||c.relname||'|'||t.tgname||'|'||t.tgenabled::text||'|'||pg_get_triggerdef(t.oid,false)
                FROM pg_trigger t JOIN pg_class c ON c.oid=t.tgrelid JOIN pg_namespace n ON n.oid=c.relnamespace
                WHERE n.nspname='public' AND NOT t.tgisinternal AND c.relname<>'flyway_schema_history'
                UNION ALL
                SELECT 'function|'||p.proname||'|'||pg_get_function_identity_arguments(p.oid)||'|'||pg_get_functiondef(p.oid)
                FROM pg_proc p JOIN pg_namespace n ON n.oid=p.pronamespace WHERE n.nspname='public' AND p.prokind IN ('f','p')
                UNION ALL
                SELECT 'sequence|'||c.relname||'|'||format_type(s.seqtypid,null)||'|'||s.seqstart||'|'||s.seqincrement
                    ||'|'||s.seqmax||'|'||s.seqmin||'|'||s.seqcache||'|'||s.seqcycle
                FROM pg_sequence s JOIN pg_class c ON c.oid=s.seqrelid JOIN pg_namespace n ON n.oid=c.relnamespace
                WHERE n.nspname='public'
                UNION ALL
                SELECT 'policy|'||c.relname||'|'||p.polname||'|'||p.polcmd::text||'|'||p.polpermissive||'|'
                    ||coalesce(pg_get_expr(p.polqual,p.polrelid),'')||'|'||coalesce(pg_get_expr(p.polwithcheck,p.polrelid),'')
                FROM pg_policy p JOIN pg_class c ON c.oid=p.polrelid JOIN pg_namespace n ON n.oid=c.relnamespace
                WHERE n.nspname='public'
                """, String.class);
    }

    public static List<String> historial(JdbcTemplate jdbc) {
        return jdbc.queryForList("""
                SELECT coalesce(version,'<repeatable>')||'|'||type||'|'||script||'|'
                    ||coalesce(checksum::text,'<null>')||'|'||success FROM public.flyway_schema_history
                """, String.class);
    }

    public static String huella(List<String> filas) {
        try {
            var digest = MessageDigest.getInstance("SHA-256");
            var normalizadas = filas.stream().map(s -> s.replace("\r\n", "\n")).sorted().toList();
            // Length-prefix prevents ambiguity when SQL function bodies contain row separators.
            for (var fila : normalizadas) {
                byte[] bytes = fila.getBytes(StandardCharsets.UTF_8);
                digest.update((bytes.length + ":").getBytes(StandardCharsets.UTF_8)); digest.update(bytes);
            }
            return HexFormat.of().formatHex(digest.digest());
        } catch (java.security.NoSuchAlgorithmException ex) { throw new IllegalStateException(ex); }
    }

    private static IllegalStateException pendiente(String detalle) {
        return new IllegalStateException("M4-ESQUEMA-PENDIENTE: " + detalle);
    }
}
