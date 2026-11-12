package com.prisaly.m7.infrastructure;

import com.prisaly.m1.application.port.CodigoAccesoDeliveryCommand;
import com.prisaly.m1.application.port.StudentAccessDeliveryPort;
import com.prisaly.m7.application.CifradorCodigoAcceso;
import java.sql.Timestamp;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * D-B2.5 / DD02-12a: writes the encrypted delivery in the SAME transaction as the M1 credential
 * ({@code Propagation.MANDATORY}); a failure here rolls the credential back. SMTP happens later in the worker.
 * The plain code exists only in memory between M1 and the cipher.
 */
public class OutboxCodigoAccesoAdapter implements StudentAccessDeliveryPort {

    private final JdbcTemplate jdbc;
    private final CifradorCodigoAcceso cifrador;

    public OutboxCodigoAccesoAdapter(JdbcTemplate jdbc, CifradorCodigoAcceso cifrador) {
        this.jdbc = jdbc;
        this.cifrador = cifrador;
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public void entregarCodigo(CodigoAccesoDeliveryCommand c) {
        CifradorCodigoAcceso.Cifrado cifrado = cifrador.cifrar(c.challengeId(), c.correoDestinatario(), c.codigoTemporal());
        jdbc.update("""
                INSERT INTO "EntregaCodigoAcceso" ("idEntrega", "challengeId", "claveIdempotencia", "cargaCifrada", "nonce",
                  "kidCifrado", "estado", "intentos", "proximoIntento", "fechaCreacion", "fechaExpiracion")
                VALUES (?, ?, ?, ?, ?, ?, 'PENDIENTE', 0, now(), now(), ?)
                """, UUID.randomUUID(), UUID.fromString(c.challengeId()), c.claveIdempotencia(), cifrado.carga(),
                cifrado.nonce(), cifrado.kid(), Timestamp.from(c.fechaExpiracion()));
    }
}
