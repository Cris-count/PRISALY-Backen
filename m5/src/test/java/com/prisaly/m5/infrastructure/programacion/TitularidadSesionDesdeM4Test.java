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
import com.prisaly.m5.application.ReferenciaSesion;
import com.prisaly.m5.application.acceso.IdentificadorEstudiante;

/**
 * D01: resolución de la convocatoria del estudiante en una sesión exclusivamente mediante el
 * contrato de M4. M5 no lee tablas para esta resolución: el doble de la base falla si se usa.
 */
@DisplayName("TitularidadIntentosDesdeM4: convocatoria propia en una sesión (D01)")
class TitularidadSesionDesdeM4Test {

    private static final UUID SESION = UUID.randomUUID();
    private static final UUID CONVOCATORIA = UUID.randomUUID();
    private static final UUID ANA = UUID.randomUUID();

    private final List<String> consultasM4 = new ArrayList<>();
    private Optional<ContextoProgramacion> respuesta = Optional.empty();
    private boolean m4Caido;

    private final JdbcTemplate base = new JdbcTemplate() {
        @Override
        public <T> List<T> queryForList(String sql, Class<T> tipo, Object... argumentos) {
            throw new AssertionError("la resolución por sesión no lee tablas de M5 ni de M4");
        }
    };

    private final ProgramacionParticipacionPort m4 = new ProgramacionParticipacionPort() {
        @Override
        public Optional<ContextoProgramacion> consultarSesion(UUID sesionId, UUID estudiante) {
            consultasM4.add(sesionId + "/" + estudiante);
            if (m4Caido) {
                throw new ConsultaProgramacionNoDisponibleException();
            }
            return respuesta;
        }

        @Override
        public Optional<ContextoProgramacion> consultarConvocatoria(UUID convocatoria, UUID estudiante) {
            throw new AssertionError("la resolución por sesión usa consultarSesion");
        }
    };

    private final TitularidadIntentosDesdeM4 titularidad = new TitularidadIntentosDesdeM4(base, m4);

    @Test
    @DisplayName("el contexto que coincide con sesión y estudiante devuelve su convocatoria")
    void convocatoriaPropia() {
        respuesta = Optional.of(contexto(SESION, ANA));

        assertEquals(Optional.of(new ReferenciaConvocatoria(CONVOCATORIA.toString())),
                titularidad.convocatoriaEnSesion(sesion(SESION), estudiante(ANA)));
        assertEquals(List.of(SESION + "/" + ANA), consultasM4);
    }

    @Test
    @DisplayName("sin convocatoria propia en la sesión no se resuelve ninguna")
    void sinConvocatoria() {
        assertEquals(Optional.empty(), titularidad.convocatoriaEnSesion(sesion(SESION), estudiante(ANA)));
    }

    @Test
    @DisplayName("un contexto de otra sesión o de otro estudiante se descarta")
    void contextoIncoherente() {
        respuesta = Optional.of(contexto(UUID.randomUUID(), ANA));
        assertEquals(Optional.empty(), titularidad.convocatoriaEnSesion(sesion(SESION), estudiante(ANA)));

        respuesta = Optional.of(contexto(SESION, UUID.randomUUID()));
        assertEquals(Optional.empty(), titularidad.convocatoriaEnSesion(sesion(SESION), estudiante(ANA)));
    }

    @Test
    @DisplayName("referencias que no son uuid no llegan a M4")
    void referenciasNoUuid() {
        assertEquals(Optional.empty(), titularidad.convocatoriaEnSesion(new ReferenciaSesion("x"), estudiante(ANA)));
        assertEquals(Optional.empty(), titularidad.convocatoriaEnSesion(sesion(SESION), new IdentificadorEstudiante("x")));
        assertEquals(List.of(), consultasM4);
    }

    @Test
    @DisplayName("una respuesta nula de M4 se trata como ausencia")
    @SuppressWarnings("OptionalAssignedToNull")
    void respuestaNula() {
        respuesta = null;
        assertEquals(Optional.empty(), titularidad.convocatoriaEnSesion(sesion(SESION), estudiante(ANA)));
    }

    @Test
    @DisplayName("la indisponibilidad de M4 (incluida la ambigüedad) se propaga")
    void m4Indisponible() {
        m4Caido = true;
        assertThrows(ConsultaProgramacionNoDisponibleException.class,
                () -> titularidad.convocatoriaEnSesion(sesion(SESION), estudiante(ANA)));
    }

    private static ReferenciaSesion sesion(UUID id) {
        return new ReferenciaSesion(id.toString());
    }

    private static IdentificadorEstudiante estudiante(UUID id) {
        return new IdentificadorEstudiante(id.toString());
    }

    private static ContextoProgramacion contexto(UUID sesion, UUID estudiante) {
        Instant ahora = Instant.now();
        return new ContextoProgramacion(CONVOCATORIA, estudiante, sesion, UUID.randomUUID(),
                "SinIniciar", "Autorizado", Optional.empty(), "Publicado", true,
                ahora.minusSeconds(3600), ahora.plusSeconds(3600), Optional.empty(), Optional.empty(),
                Duration.ofMinutes(30), Duration.ofMinutes(20), ahora);
    }
}
