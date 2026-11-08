package com.prisaly.config;

import org.flywaydb.core.Flyway;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.postgresql.PostgreSQLContainer;

/** Only disposable Testcontainers, no external URL or shared database accepted. */
final class M4SchemaFixture {
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
    static JdbcTemplate jdbc(PostgreSQLContainer db) {
        return new JdbcTemplate(new DriverManagerDataSource(db.getJdbcUrl(), db.getUsername(), db.getPassword()));
    }
}
