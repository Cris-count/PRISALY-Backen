package com.prisaly.m4.infrastructure;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

import java.nio.file.*;
import java.security.MessageDigest;
import java.util.Collections;

class M4MigrationResourcesTest {
    @Test
    void propuestaV010NoSeDescubreAutomaticamenteYSeConservaIntacta() throws Exception {
        var loader = getClass().getClassLoader();
        assertTrue(
                Collections.list(
                                loader.getResources(
                                        "db/migration/V010__m4_sesiones_convocatorias.sql"))
                        .isEmpty());
        try (var archived =
                loader.getResourceAsStream("db/m4-propuesta/V010__m4_sesiones_convocatorias.sql")) {
            assertNotNull(archived);
            var fuente =
                    Path.of("src/main/resources/db/migration/V010__m4_sesiones_convocatorias.sql");
            assertArrayEquals(
                    MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(fuente)),
                    MessageDigest.getInstance("SHA-256").digest(archived.readAllBytes()));
        }
    }
}
