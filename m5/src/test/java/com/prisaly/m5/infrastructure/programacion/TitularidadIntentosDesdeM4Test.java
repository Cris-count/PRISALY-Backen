package com.prisaly.m5.infrastructure.programacion;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.jdbc.core.JdbcTemplate;

import com.prisaly.m4.api.ConsultaProgramacionNoDisponibleException;
import com.prisaly.m4.api.ContextoProgramacion;
import com.prisaly.m4.api.ProgramacionParticipacionPort;
import com.prisaly.m5.application.ReferenciaIntento;
import com.prisaly.m5.application.acceso.IdentificadorEstudiante;
import com.prisaly.m5.application.acceso.Titularidad;
import com.prisaly.m5.infrastructure.persistencia.PersistenciaIntentoException;

/**
 * Lógica del adaptador con dobles de la base y de M4. La integración real con
 * PostgreSQL y el adaptador JDBC de M4 está en {@code AccesoEstudianteM4M5IT}.
 */
@DisplayName("TitularidadIntentosDesdeM4")
class TitularidadIntentosDesdeM4Test {

    private static final UUID INTENTO = UUID.randomUUID();
    private static final UUID CONVOCATORIA = UUID.randomUUID();
    private static final UUID ANA = UUID.randomUUID();
    private static final UUID LUIS = UUID.randomUUID();

    private final List<String> consultasM4 = new ArrayList<>();
    private Optional<UUID> convocatoriaDelIntento = Optional.of(CONVOCATORIA);
    private RuntimeException falloBase;

    /** Base simulada: solo responde la consulta propia de M5 (Intento → Participacion). */
    private final JdbcTemplate base = new JdbcTemplate() {
        @Override
        public <T> List<T> queryForList(String sql, Class<T> tipo, Object... argumentos) {
            assertEquals(TitularidadIntentosDesdeM4.CONVOCATORIA_DEL_INTENTO, sql);
            assertFalse(sql.contains("\"Convocatoria\""), "M5 no lee la tabla de M4");
            if (falloBase != null) {
                throw falloBase;
            }
            return convocatoriaDelIntento.filter(c -> INTENTO.equals(argumentos[0]))
                    .map(tipo::cast).map(List::of).orElse(List.of());
        }
    };

    /** M4 simulado con su semántica publicada: vacío si la convocatoria no es del estudiante. */
    private ProgramacionParticipacionPort m4 = new ProgramacionParticipacionPort() {
        @Override
        public Optional<ContextoProgramacion> consultarSesion(UUID sesionId, UUID estudiante) {
            throw new AssertionError("M5 no consulta por sesión");
        }

        @Override
        public Optional<ContextoProgramacion> consultarConvocatoria(UUID convocatoria, UUID estudiante) {
            consultasM4.add(convocatoria + "/" + estudiante);
            return CONVOCATORIA.equals(convocatoria) && ANA.equals(estudiante)
                    ? Optional.of(contexto(convocatoria, estudiante))
                    : Optional.empty();
        }
    };

    private Titularidad de(String intento, String estudiante) {
        return new TitularidadIntentosDesdeM4(base, m4)
                .de(new ReferenciaIntento(intento), new IdentificadorEstudiante(estudiante));
    }

    @Test
    @DisplayName("el estudiante de la convocatoria es titular")
    void titular() {
        assertEquals(Titularidad.PROPIO, de(INTENTO.toString(), ANA.toString()));
        assertEquals(List.of(CONVOCATORIA + "/" + ANA), consultasM4);
    }

    @Test
    @DisplayName("otro estudiante: M4 responde vacío y el intento es ajeno")
    void ajeno() {
        assertEquals(Titularidad.AJENO, de(INTENTO.toString(), LUIS.toString()));
    }

    @Test
    @DisplayName("un intento sin participación es inexistente y no se consulta a M4")
    void inexistente() {
        assertEquals(Titularidad.INEXISTENTE, de(UUID.randomUUID().toString(), ANA.toString()));
        assertEquals(Titularidad.INEXISTENTE, de("no-es-uuid", ANA.toString()));
        assertEquals(List.of(), consultasM4);
    }

    @Test
    @DisplayName("un identificador de estudiante que no es uuid nunca es titular")
    void estudianteNoUuid() {
        assertEquals(Titularidad.AJENO, de(INTENTO.toString(), "E-ana"));
        assertEquals(List.of(), consultasM4);
    }

    @Test
    @DisplayName("un contexto de M4 que no coincide con lo consultado se trata como ajeno")
    void contextoIncoherente() {
        m4 = new ProgramacionParticipacionPort() {
            @Override
            public Optional<ContextoProgramacion> consultarSesion(UUID sesionId, UUID estudiante) {
                throw new AssertionError();
            }

            @Override
            public Optional<ContextoProgramacion> consultarConvocatoria(UUID convocatoria, UUID estudiante) {
                return Optional.of(contexto(convocatoria, LUIS));
            }
        };

        assertEquals(Titularidad.AJENO, de(INTENTO.toString(), ANA.toString()));
    }

    @Test
    @DisplayName("una respuesta nula de M4 se trata como ajeno")
    void respuestaNula() {
        m4 = new ProgramacionParticipacionPort() {
            @Override
            public Optional<ContextoProgramacion> consultarSesion(UUID sesionId, UUID estudiante) {
                throw new AssertionError();
            }

            @Override
            public Optional<ContextoProgramacion> consultarConvocatoria(UUID convocatoria, UUID estudiante) {
                return null;
            }
        };

        assertEquals(Titularidad.AJENO, de(INTENTO.toString(), ANA.toString()));
    }

    @Test
    @DisplayName("la indisponibilidad de M4 se propaga tal cual (503), nunca como acceso ni como 404")
    void m4NoDisponible() {
        ConsultaProgramacionNoDisponibleException caida = new ConsultaProgramacionNoDisponibleException();
        m4 = new ProgramacionParticipacionPort() {
            @Override
            public Optional<ContextoProgramacion> consultarSesion(UUID sesionId, UUID estudiante) {
                throw caida;
            }

            @Override
            public Optional<ContextoProgramacion> consultarConvocatoria(UUID convocatoria, UUID estudiante) {
                throw caida;
            }
        };

        assertSame(caida, assertThrows(ConsultaProgramacionNoDisponibleException.class,
                () -> de(INTENTO.toString(), ANA.toString())));
    }

    @Test
    @DisplayName("un fallo de la consulta propia se envuelve sin exponer el SQL")
    void falloDeLaBase() {
        falloBase = new DataAccessResourceFailureException("SELECT secreto");

        PersistenciaIntentoException error = assertThrows(PersistenciaIntentoException.class,
                () -> de(INTENTO.toString(), ANA.toString()));
        assertFalse(error.getMessage().contains("SELECT"));
    }

    private static ContextoProgramacion contexto(UUID convocatoria, UUID estudiante) {
        Instant ahora = Instant.parse("2026-11-01T12:00:00Z");
        return new ContextoProgramacion(convocatoria, estudiante, UUID.randomUUID(), UUID.randomUUID(),
                "EnCurso", "Autorizado", Optional.empty(), "Publicado", true,
                ahora.minusSeconds(3600), ahora.plusSeconds(3600), Optional.empty(), Optional.empty(),
                Duration.ofMinutes(30), ahora);
    }
}
