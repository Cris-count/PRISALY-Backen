package com.prisaly.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

/**
 * DC-DD02-B4-B5-01: database-level guarantees of the optional candidates V014-V016 (db/dd02b-propuesta), applied over
 * the canonical V001-V009 + V013 in a disposable container. Not part of the productive Flyway location.
 */
@Testcontainers
class EsquemaDd02bPostgreSqlIT {
    @Container static PostgreSQLContainer db = new PostgreSQLContainer("postgres:16.15");
    JdbcTemplate jdbc;
    UUID usuario, estudiante, otroUsuario, otroEstudiante;

    @BeforeEach
    void migrar() {
        var flyway = M4SchemaFixture.flyway(db, "classpath:db/migration", "classpath:db/dd02b-propuesta");
        flyway.clean();
        flyway.migrate();
        flyway.validate();
        jdbc = M4SchemaFixture.jdbc(db);
        jdbc.update("INSERT INTO \"Rol\" (\"nombre\",\"descripcion\") VALUES ('EstQA','Fixture')");
        usuario = UUID.randomUUID(); estudiante = UUID.randomUUID(); otroUsuario = UUID.randomUUID(); otroEstudiante = UUID.randomUUID();
        for (UUID[] par : new UUID[][] {{usuario, estudiante}, {otroUsuario, otroEstudiante}}) {
            jdbc.update("INSERT INTO \"Usuario\" (\"idUsuario\",\"idRol\",\"nombre\",\"correo\") SELECT ?, \"idRol\", 'QA', ? FROM \"Rol\"",
                    par[0], par[0] + "@prisaly.test");
            jdbc.update("INSERT INTO \"Estudiante\" (\"idEstudiante\",\"idUsuario\") VALUES (?,?)", par[1], par[0]);
        }
    }

    private void credencial(UUID u, UUID e, String expira) {
        jdbc.update("INSERT INTO \"CredencialAccesoEstudiante\" (\"idCredencial\",\"idUsuario\",\"idEstudiante\",\"challengeId\",\"codigoHmac\",\"kidCodigo\",\"fechaExpiracion\") "
                + "VALUES (?,?,?,?,'\\x01','k', now() + ?::interval)", UUID.randomUUID(), u, e, UUID.randomUUID(), expira);
    }

    private static void rechaza(String marca, Runnable r) {
        try {
            r.run();
        } catch (DataAccessException ex) {
            assertTrue(String.valueOf(ex.getMostSpecificCause().getMessage()).contains(marca), ex.getMostSpecificCause().getMessage());
            return;
        }
        throw new AssertionError("expected rejection " + marca);
    }

    @Test
    void vinculoCruzadoYCuentaInactivaSeRechazan() {
        rechaza("DD02-VINCULO-INCOHERENTE", () -> credencial(usuario, otroEstudiante, "4 minutes"));
        jdbc.update("UPDATE \"Usuario\" SET \"estado\"='Inactivo' WHERE \"idUsuario\"=?", otroUsuario);
        rechaza("DD02-CUENTA-INACTIVA", () -> credencial(otroUsuario, otroEstudiante, "4 minutes"));
    }

    @Test
    void vigenciaMaximaYUnaSolaCredencialAbiertaPorCuenta() {
        rechaza("ck_credencialaccesoestudiante_vigencia", () -> credencial(usuario, estudiante, "6 minutes"));
        credencial(usuario, estudiante, "5 minutes");
        rechaza("uq_credencialaccesoestudiante_abierta", () -> credencial(usuario, estudiante, "5 minutes"));
        jdbc.update("UPDATE \"CredencialAccesoEstudiante\" SET \"fechaRevocacion\"=now() WHERE \"idUsuario\"=?", usuario);
        credencial(usuario, estudiante, "5 minutes");
        rechaza("ck_credencialaccesoestudiante_intentos",
                () -> jdbc.update("UPDATE \"CredencialAccesoEstudiante\" SET \"intentosFallidos\"=6 WHERE \"idUsuario\"=?", usuario));
    }

    @Test
    void elVinculoEnUsoNoSeReasignaYLaSesionNoSuperaOchoHoras() {
        credencial(usuario, estudiante, "5 minutes");
        rechaza("DD02-VINCULO-EN-USO", () -> jdbc.update("UPDATE \"Estudiante\" SET \"idUsuario\"=? WHERE \"idEstudiante\"=?", otroUsuario, estudiante));
        rechaza("ck_sesionestudiante_vigencia", () -> jdbc.update(
                "INSERT INTO \"SesionEstudiante\" (\"idSesionEstudiante\",\"idUsuario\",\"idEstudiante\",\"sesionTokenHmac\",\"kidSesion\",\"fechaExpiracion\") "
                        + "VALUES (?,?,?,'\\x02','s', now() + interval '9 hours')", UUID.randomUUID(), usuario, estudiante));
    }

    @Test
    void laColaExigeCargaCifradaSoloMientrasEstaPendiente() {
        String insert = "INSERT INTO \"EntregaCodigoAcceso\" (\"idEntrega\",\"challengeId\",\"claveIdempotencia\",\"cargaCifrada\",\"nonce\",\"kidCifrado\",\"estado\",\"fechaExpiracion\") "
                + "VALUES (?,?,?,?,?,?,?, now() + interval '5 minutes')";
        UUID ch = UUID.randomUUID();
        rechaza("ck_entregacodigoacceso_carga", () -> jdbc.update(insert, UUID.randomUUID(), ch, "a:" + ch, null, null, null, "PENDIENTE"));
        jdbc.update(insert, UUID.randomUUID(), ch, "a:" + ch, new byte[] {1}, new byte[12], "o", "PENDIENTE");
        rechaza("uq_entregacodigoacceso_challenge", () -> jdbc.update(insert, UUID.randomUUID(), ch, "b:" + ch, new byte[] {1}, new byte[12], "o", "PENDIENTE"));
        UUID enviada = UUID.randomUUID();
        jdbc.update(insert, UUID.randomUUID(), enviada, "c:" + enviada, null, null, null, "ENVIADA");
        rechaza("ck_entregacodigoacceso_carga", () -> jdbc.update(insert, UUID.randomUUID(), UUID.randomUUID(), "e", new byte[] {1}, new byte[12], "o", "ENVIADA"));
        rechaza("ck_entregacodigoacceso_estado", () -> jdbc.update(insert, UUID.randomUUID(), UUID.randomUUID(), "d", null, null, null, "OTRO"));
        assertEquals(2, jdbc.queryForObject("SELECT count(*) FROM \"EntregaCodigoAcceso\"", Integer.class));
    }

    private static final String[] ESTADOS = {"PENDIENTE", "ENVIADA", "CANCELADA_VENCIDA", "FALLIDA_PERMANENTE", "AGOTADA"};
    private static final String INSERT_ENTREGA = "INSERT INTO \"EntregaCodigoAcceso\" (\"idEntrega\",\"challengeId\",\"claveIdempotencia\","
            + "\"cargaCifrada\",\"nonce\",\"kidCifrado\",\"estado\",\"fechaExpiracion\") VALUES (?,?,?,?,?,?,?, now() + interval '5 minutes')";

    private int insertarEntrega(UUID id, String estado, boolean carga, boolean nonce, boolean kid) {
        UUID ch = UUID.randomUUID();
        return jdbc.update(INSERT_ENTREGA, id, ch, "m:" + ch, carga ? new byte[] {1} : null, nonce ? new byte[12] : null,
                kid ? "o" : null, estado);
    }

    /**
     * DC-EJEC-CIERRE-01 (DD02-REV-01): the 5 states x the 8 presence combinations of (cargaCifrada, nonce, kidCifrado).
     * Exactly two shapes are valid: PENDIENTE with the three present, and a final state with the three absent.
     * The other 35 combinations, including every partial residue in a final state, must be rejected by the CHECK.
     */
    @Test
    void matrizCompletaEstadoPorCargaSoloAceptaLasDosFormasCoherentes() {
        int aceptadas = 0, rechazadas = 0;
        for (String estado : ESTADOS) {
            for (int mascara = 0; mascara < 8; mascara++) {
                boolean carga = (mascara & 4) != 0, nonce = (mascara & 2) != 0, kid = (mascara & 1) != 0;
                boolean valida = "PENDIENTE".equals(estado) ? (carga && nonce && kid) : (!carga && !nonce && !kid);
                String caso = estado + " carga=" + carga + " nonce=" + nonce + " kid=" + kid;
                if (valida) {
                    assertEquals(1, insertarEntrega(UUID.randomUUID(), estado, carga, nonce, kid), caso);
                    aceptadas++;
                } else {
                    rechaza("ck_entregacodigoacceso_carga", () -> insertarEntrega(UUID.randomUUID(), estado, carga, nonce, kid));
                    rechazadas++;
                }
            }
        }
        assertEquals(5, aceptadas);
        assertEquals(35, rechazadas);
        assertEquals(5, jdbc.queryForObject("SELECT count(*) FROM \"EntregaCodigoAcceso\"", Integer.class));
    }

    /** DD02-REV-01 by UPDATE: a finalization that leaves any residue fails; only the complete purge succeeds. */
    @Test
    void laFinalizacionParcialSeRechazaYSoloLaPurgaCompletaSeAcepta() {
        UUID id = UUID.randomUUID();
        insertarEntrega(id, "PENDIENTE", true, true, true);
        String[] parciales = {
            "\"nonce\"=NULL, \"kidCifrado\"=NULL",          // payload without nonce
            "\"cargaCifrada\"=NULL, \"kidCifrado\"=NULL",   // nonce without payload
            "\"cargaCifrada\"=NULL, \"nonce\"=NULL",        // key id alone
            "\"cargaCifrada\"=NULL",                        // nonce and key id left
            ""};                                              // nothing purged
        for (String estado : new String[] {"ENVIADA", "CANCELADA_VENCIDA", "FALLIDA_PERMANENTE", "AGOTADA"}) {
            for (String parcial : parciales) {
                String set = "\"estado\"='" + estado + "'" + (parcial.isEmpty() ? "" : ", " + parcial);
                rechaza("ck_entregacodigoacceso_carga",
                        () -> jdbc.update("UPDATE \"EntregaCodigoAcceso\" SET " + set + " WHERE \"idEntrega\"=?", id));
            }
        }
        rechaza("ck_entregacodigoacceso_carga", () -> jdbc.update(
                "UPDATE \"EntregaCodigoAcceso\" SET \"nonce\"=NULL WHERE \"idEntrega\"=?", id)); // pending loses part
        assertEquals(1, jdbc.update("UPDATE \"EntregaCodigoAcceso\" SET \"estado\"='ENVIADA', \"cargaCifrada\"=NULL, "
                + "\"nonce\"=NULL, \"kidCifrado\"=NULL WHERE \"idEntrega\"=?", id));
        rechaza("ck_entregacodigoacceso_carga", () -> jdbc.update(
                "UPDATE \"EntregaCodigoAcceso\" SET \"nonce\"='\\x00' WHERE \"idEntrega\"=?", id)); // residue re-added
        assertEquals(0, jdbc.queryForObject("SELECT count(*) FROM \"EntregaCodigoAcceso\" WHERE \"idEntrega\"=? AND "
                + "(\"cargaCifrada\" IS NOT NULL OR \"nonce\" IS NOT NULL OR \"kidCifrado\" IS NOT NULL)", Integer.class, id));
    }
}
