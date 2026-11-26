package com.prisaly.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.prisaly.m5.domain.investigacion.Edificio;
import com.prisaly.m5.domain.investigacion.Nota;
import com.prisaly.m5.domain.investigacion.Pista;
import com.prisaly.m5.domain.investigacion.Zona;
import com.prisaly.m5.infrastructure.persistencia.RepositorioInvestigacionJpa;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

@Testcontainers
class InvestigacionDc116PostgreSqlIT {
    @Container
    static final PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:16.15");

    @Test
    void migraSobreEsquemaVigenteYConservaElProgresoPorIntento() {
        var vigente = M4SchemaFixture.flyway(postgres, "classpath:db/migration");
        assertEquals(10, vigente.migrate().migrationsExecuted);
        var jdbc = M4SchemaFixture.jdbc(postgres);
        var intento = insertarIntento(jdbc);

        var candidato = M4SchemaFixture.flyway(postgres, "classpath:db/migration",
                "classpath:db/dc116-propuesta");
        assertEquals(1, candidato.migrate().migrationsExecuted);
        candidato.validate();
        assertEquals(0, candidato.migrate().migrationsExecuted);

        var escritura = new RepositorioInvestigacionJpa(jdbc);
        var lectura = new RepositorioInvestigacionJpa(M4SchemaFixture.jdbc(postgres));
        var primero = Instant.parse("2026-01-01T10:00:00Z");
        var segundo = primero.plusSeconds(1);
        var notaA = new Nota(UUID.randomUUID(), intento, "Nota A", "Texto A", primero);
        var notaB = new Nota(UUID.randomUUID(), intento, "Nota B", "Texto B", segundo);
        var pistaA = new Pista(UUID.randomUUID(), intento, "Pista A", primero);
        var pistaB = new Pista(UUID.randomUUID(), intento, "Pista B", segundo);
        var edificioA = new Edificio(UUID.randomUUID(), intento, "Edificio A", null, primero);
        var edificioB = new Edificio(UUID.randomUUID(), intento, "Edificio B", "Norte", segundo);
        var zonaA = new Zona(UUID.randomUUID(), intento, "Zona A", null, primero);
        var zonaB = new Zona(UUID.randomUUID(), intento, "Zona B", "Sur", segundo);

        escritura.insertar(notaB);
        escritura.insertar(pistaB);
        escritura.insertar(edificioB);
        escritura.insertar(zonaB);
        escritura.insertar(notaA);
        escritura.insertar(pistaA);
        escritura.insertar(edificioA);
        escritura.insertar(zonaA);

        assertEquals(List.of(notaA, notaB), lectura.notasDeIntento(intento));
        assertEquals(List.of(pistaA, pistaB), lectura.pistasDeIntento(intento));
        assertEquals(List.of(edificioA, edificioB), lectura.edificiosDeIntento(intento));
        assertEquals(List.of(zonaA, zonaB), lectura.zonasDeIntento(intento));

        var ajeno = UUID.randomUUID();
        assertEquals(List.of(), lectura.notasDeIntento(ajeno));
        assertEquals(List.of(), lectura.pistasDeIntento(ajeno));
        assertEquals(List.of(), lectura.edificiosDeIntento(ajeno));
        assertEquals(List.of(), lectura.zonasDeIntento(ajeno));
        assertThrows(DataIntegrityViolationException.class,
                () -> escritura.insertar(new Nota(UUID.randomUUID(), ajeno, "Nota", "Texto", primero)));
    }

    private UUID insertarIntento(JdbcTemplate jdbc) {
        var docente = UUID.randomUUID();
        var usuario = UUID.randomUUID();
        var estudiante = UUID.randomUUID();
        var caso = UUID.randomUUID();
        var sesion = UUID.randomUUID();
        var convocatoria = UUID.randomUUID();
        var participacion = UUID.randomUUID();
        var intento = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO "Rol" ("idRol","nombre","descripcion") VALUES (1,'Fixture','Prueba')
                """);
        jdbc.update("""
                INSERT INTO "Usuario" ("idUsuario","idRol","nombre","correo")
                VALUES (?,1,'Docente','docente@example.invalid'),(?,1,'Estudiante','estudiante@example.invalid')
                """, docente, usuario);
        jdbc.update("""
                INSERT INTO "Estudiante" ("idEstudiante","idUsuario") VALUES (?,?)
                """, estudiante, usuario);
        jdbc.update("""
                INSERT INTO "Caso" ("idCaso","idDocente","nombre","tiempoEstimadoSegundos","estado")
                VALUES (?,?,'Caso DC-116',600,'Publicado')
                """, caso, docente);
        jdbc.update("""
                INSERT INTO "Sesion" ("idSesion","idCaso","idDocenteProgramador","nombreEspacioAcademico",
                  "inicioPrevisto","finPrevisto","tiempoMaximoParticipacionSegundos")
                VALUES (?,?,?,'DC-116',now()-interval '1 hour',now()+interval '1 hour',3600)
                """, sesion, caso, docente);
        jdbc.update("""
                INSERT INTO "Convocatoria" ("idConvocatoria","idSesion","idEstudiante") VALUES (?,?,?)
                """, convocatoria, sesion, estudiante);
        jdbc.update("""
                INSERT INTO "Participacion" ("idParticipacion","idConvocatoria") VALUES (?,?)
                """, participacion, convocatoria);
        jdbc.update("""
                INSERT INTO "Intento" ("idIntento","idParticipacion","numeroIntento","inicio","limiteTemporal","estado")
                VALUES (?,?,1,now(),now()+interval '1 hour','EnCurso')
                """, intento, participacion);
        return intento;
    }
}
