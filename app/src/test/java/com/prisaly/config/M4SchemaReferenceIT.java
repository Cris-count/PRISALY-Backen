package com.prisaly.config;

import com.prisaly.m4.infrastructure.config.M4SchemaContract;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

/** Explicit reference generation, never called by the application or normal guard. */
@Testcontainers
class M4SchemaReferenceIT {
    @Container static PostgreSQLContainer db = new PostgreSQLContainer("postgres:16.15");
    @Test void exportaReferenciasDesdeSqlCanonicoSinTargetNiRepair() throws Exception {
        Path output = Path.of("target/m4-schema-canonical"); Files.createDirectories(output);
        for (boolean dd02 : List.of(false, true)) {
            var jdbc = M4SchemaFixture.restaurar(db, dd02);
            String contract = dd02 ? M4SchemaContract.DD02 : M4SchemaContract.HISTORICO;
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
}
