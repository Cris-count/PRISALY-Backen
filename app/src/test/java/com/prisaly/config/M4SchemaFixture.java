package com.prisaly.config;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.stream.Stream;
import org.flywaydb.core.Flyway;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.postgresql.PostgreSQLContainer;

/** Only disposable Testcontainers, no external URL or shared database accepted. */
final class M4SchemaFixture {
    /** V001-V009, the historical BD-005.2 baseline (bd0052-v009). P5 adds V013 to the canonical location. */
    static final String[] HISTORICAS = {
        "V001__bd0052_estructura.sql", "V002__bd0052_restricciones.sql", "V003__bd0052_integridad.sql",
        "V004__bd0052_integridad_intentos.sql", "V005__bd0052_cierre_integridad.sql",
        "V006__bd0052_proteccion_relaciones.sql", "V007__fase3_politicas_aprobadas.sql",
        "V008__bloqueo_caso_al_reasignar_sesion.sql", "V009__rf09_solo_casos_publicados.sql"};

    private M4SchemaFixture() { }
    static Flyway flyway(PostgreSQLContainer db, String... locations) {
        return Flyway.configure().dataSource(db.getJdbcUrl(), db.getUsername(), db.getPassword())
                .locations(locations).cleanDisabled(false).load();
    }
    static JdbcTemplate restaurar(PostgreSQLContainer db, boolean dd02) {
        if (!db.isRunning()) throw new IllegalStateException("Requires own running PostgreSQLContainer");
        var flyway = dd02 ? flyway(db, "classpath:db/migration", "classpath:db/dd02-propuesta")
                : flyway(db, "classpath:db/migration");
        flyway.clean(); flyway.migrate(); flyway.validate();
        return jdbc(db);
    }
    /** Historical baseline only (V001-V009 copied byte for byte from the classpath, without V013). */
    static JdbcTemplate restaurarHistorico(PostgreSQLContainer db) throws IOException {
        if (!db.isRunning()) throw new IllegalStateException("Requires own running PostgreSQLContainer");
        Path dir = historicoSinV013();
        try {
            var flyway = flyway(db, "filesystem:" + dir.toAbsolutePath());
            flyway.clean(); flyway.migrate(); flyway.validate();
            return jdbc(db);
        } finally {
            borrar(dir);
        }
    }
    static Path historicoSinV013() throws IOException {
        Path dir = Files.createTempDirectory("bd0052-v009-");
        for (String nombre : HISTORICAS) {
            try (InputStream in = M4SchemaFixture.class.getResourceAsStream("/db/migration/" + nombre)) {
                if (in == null) throw new IllegalStateException("Missing canonical migration " + nombre);
                Files.copy(in, dir.resolve(nombre));
            }
        }
        return dir;
    }
    static void borrar(Path dir) throws IOException {
        try (Stream<Path> paths = Files.walk(dir)) {
            for (Path p : paths.sorted(Comparator.reverseOrder()).toList()) Files.deleteIfExists(p);
        }
    }
    static JdbcTemplate jdbc(PostgreSQLContainer db) {
        return new JdbcTemplate(new DriverManagerDataSource(db.getJdbcUrl(), db.getUsername(), db.getPassword()));
    }
}
