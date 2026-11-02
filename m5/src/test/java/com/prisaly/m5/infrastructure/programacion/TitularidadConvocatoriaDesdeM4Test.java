package com.prisaly.m5.infrastructure.programacion;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

import com.prisaly.m4.api.ConsultaProgramacionNoDisponibleException;
import com.prisaly.m4.api.ContextoProgramacion;
import com.prisaly.m4.api.ProgramacionParticipacionPort;
import com.prisaly.m5.application.ReferenciaConvocatoria;
import com.prisaly.m5.application.acceso.IdentificadorEstudiante;
import com.prisaly.m5.application.acceso.Titularidad;

/**
 * Titularidad de una convocatoria sobre el contrato de M4, con dobles. M5 no
 * consulta su base para esta verificación: el doble de la base falla si se usa.
 */
@DisplayName("TitularidadIntentosDesdeM4: convocatorias")
class TitularidadConvocatoriaDesdeM4Test {

    private static final UUID CONVOCATORIA = UUID.randomUUID();
    private static final UUID ANA = UUID.randomUUID();
    private static final UUID LUIS = UUID.randomUUID();

    private final List<String> consultasM4 = new ArrayList<>();
    private ContextoProgramacion respuestaAnomala;
    private boolean m4Caido;
    private boolean respuestaNula;

    private final JdbcTemplate base = new JdbcTemplate() {
        @Override
        public <T> List<T> queryForList(String sql, Class<T> tipo, Object... argumentos) {
            throw new AssertionError("la titularidad de una convocatoria no lee tablas");
        }
    };

    /** M4 con su semántica publicada: vacío si la convocatoria no existe o no es del estudiante. */
    private final ProgramacionParticipacionPort m4 = new ProgramacionParticipacionPort() {
        @Override
        public Optional<ContextoProgramacion> consultarSesion(UUID sesionId, UUID estudiante) {
            throw new AssertionError("M5 no consulta por sesión");
        }

        @Override
        @SuppressWarnings("OptionalAssignedToNull")
        public Optional<ContextoProgramacion> consultarConvocatoria(UUID convocatoria, UUID estudiante) {
            consultasM4.add(convocatoria + "/" + estudiante);
            if (m4Caido) {
                throw new ConsultaProgramacionNoDisponibleException();
            }
            if (respuestaNula) {
                return null;
            }
            if (respuestaAnomala != null) {
                return Optional.of(respuestaAnomala);
            }
            return CONVOCATORIA.equals(convocatoria) && ANA.equals(estudiante)
                    ? Optional.of(contexto(convocatoria, estudiante))
                    : Optional.empty();
        }
    };

    private Titularidad de(String convocatoria, String estudiante) {
        return new TitularidadIntentosDesdeM4(base, m4)
                .deConvocatoria(new ReferenciaConvocatoria(convocatoria), new IdentificadorEstudiante(estudiante));
    }

    @Test
    @DisplayName("el estudiante de la convocatoria es titular; M4 recibe el estudiante autenticado")
    void titular() {
        assertEquals(Titularidad.PROPIO, de(CONVOCATORIA.toString(), ANA.toString()));
        assertEquals(List.of(CONVOCATORIA + "/" + ANA), consultasM4);
    }

    @Test
    @DisplayName("convocatoria de otro estudiante: M4 responde vacío y no se distingue de una inexistente")
    void ajenaComoInexistente() {
        assertEquals(Titularidad.INEXISTENTE, de(CONVOCATORIA.toString(), LUIS.toString()));
        assertEquals(Titularidad.INEXISTENTE, de(UUID.randomUUID().toString(), ANA.toString()));
    }

    @Test
    @DisplayName("una referencia que no es uuid no existe y no llega a M4")
    void referenciaNoUuid() {
        assertEquals(Titularidad.INEXISTENTE, de("no-es-uuid", ANA.toString()));
        assertEquals(List.of(), consultasM4);
    }

    @Test
    @DisplayName("un estudiante que no es uuid no puede ser titular")
    void estudianteNoUuid() {
        assertEquals(Titularidad.AJENO, de(CONVOCATORIA.toString(), "E-ana"));
        assertEquals(List.of(), consultasM4);
    }

    @Test
    @DisplayName("un contexto de M4 que no coincide con lo consultado se deniega")
    void contextoIncoherente() {
        respuestaAnomala = contexto(CONVOCATORIA, LUIS);
        assertEquals(Titularidad.AJENO, de(CONVOCATORIA.toString(), ANA.toString()));
        respuestaAnomala = contexto(UUID.randomUUID(), ANA);
        assertEquals(Titularidad.AJENO, de(CONVOCATORIA.toString(), ANA.toString()));
    }

    @Test
    @DisplayName("una respuesta nula de M4 no da acceso")
    void respuestaNula() {
        respuestaNula = true;
        assertEquals(Titularidad.INEXISTENTE, de(CONVOCATORIA.toString(), ANA.toString()));
    }

    @Test
    @DisplayName("M4 caído: se propaga su error técnico y nunca hay acceso")
    void m4Caido() {
        m4Caido = true;
        assertThrows(ConsultaProgramacionNoDisponibleException.class, () -> de(CONVOCATORIA.toString(), ANA.toString()));
    }

    private static ContextoProgramacion contexto(UUID convocatoria, UUID estudiante) {
        Instant ahora = Instant.now();
        return new ContextoProgramacion(convocatoria, estudiante, UUID.randomUUID(), UUID.randomUUID(),
                "EnCurso", "Autorizado", Optional.empty(), "Publicado", true,
                ahora.minusSeconds(3600), ahora.plusSeconds(3600), Optional.empty(), Optional.empty(),
                Duration.ofMinutes(30), ahora);
    }
}
