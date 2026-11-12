package com.prisaly.config;

import com.prisaly.m4.infrastructure.config.M4SchemaContract;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

/**
 * Explicit reference generation, never called by the application or normal guard.
 * P5 (DC-P5-FASE-B-01): each contract is generated under its own name. The historical contracts are rebuilt from
 * V001-V009 only (without V013), so their references must stay byte-identical to the committed ones.
 */
@Testcontainers
class M4SchemaReferenceIT {
    @Container static PostgreSQLContainer db = new PostgreSQLContainer("postgres:16.15");
    @Test void exportaReferenciasDesdeSqlCanonicoSinTargetNiRepair() throws Exception {
        Path output = Path.of("target/m4-schema-canonical"); Files.createDirectories(output);
        Path historico = M4SchemaFixture.historicoSinV013();
        try {
            exportar(output, M4SchemaContract.HISTORICO, "filesystem:" + historico.toAbsolutePath());
            exportar(output, M4SchemaContract.DD02, "filesystem:" + historico.toAbsolutePath(), "classpath:db/dd02-propuesta");
            exportar(output, M4SchemaContract.V013, "classpath:db/migration");
            exportar(output, M4SchemaContract.V013_DD02, "classpath:db/migration", "classpath:db/dd02-propuesta");
            // DD-02 alternative B (DC-DD02-B4-B5-01): optional candidates V014-V016.
            exportar(output, M4SchemaContract.V013_DD02B, "classpath:db/migration", "classpath:db/dd02b-propuesta");
        } finally {
            M4SchemaFixture.borrar(historico);
        }
    }

    private static void exportar(Path output, String contract, String... locations) throws Exception {
        var flyway = M4SchemaFixture.flyway(db, locations);
        flyway.clean(); flyway.migrate(); flyway.validate();
        var jdbc = M4SchemaFixture.jdbc(db);
        var structure = M4SchemaContract.estructura(jdbc);
        var history = M4SchemaContract.historial(jdbc);
        Files.writeString(output.resolve(contract + ".properties"),
                "# Generated from cf57327 canonical SQL on PostgreSQL 16.15; DD02 remains opt-in pending approval.\n"
                + "structure-sha256=" + M4SchemaContract.huella(structure) + "\n"
                + "history-sha256=" + M4SchemaContract.huella(history) + "\n");
        Files.write(output.resolve(contract + "-structure.txt"), structure.stream().sorted().toList());
        Files.write(output.resolve(contract + "-history.txt"), history.stream().sorted().toList());
    }
}
