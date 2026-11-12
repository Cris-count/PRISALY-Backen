package com.prisaly.m1.application.port;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * DD-02 alternative B (DC-DD02-B4-B5-01): persistence of individual access codes, student sessions and access audit
 * (candidate V014-V016, optional location {@code db/dd02b-propuesta}). Replaces the alternative A
 * {@code CuentaEstudiantePort}. It never accepts a client-supplied studentId as proof of identity.
 */
public interface AccesoEstudiantePort {

    /** Student linked to an account whose registered email equals {@code correoNormalizado}. */
    Optional<VinculoEstudiante> vinculoPorCorreo(String correoNormalizado);

    Optional<VinculoEstudiante> vinculo(UUID idUsuario);

    /** D-B2.1: revokes every open credential of the user before a new emission. */
    void revocarCredencialesAbiertas(UUID idUsuario, OffsetDateTime cuando);

    void guardarCredencial(CredencialNueva credencial);

    /** Locks the credential row for the current transaction ({@code FOR UPDATE}); consumption is serialized. */
    Optional<CredencialGuardada> bloquearCredencial(UUID challengeId);

    void registrarIntentoFallido(UUID challengeId);

    /** Atomic single use: true only for the one call that consumed a still open, unexpired, non-exhausted code. */
    boolean consumir(UUID challengeId, OffsetDateTime ahora);

    void abrirSesion(SesionNueva sesion);

    /**
     * Session usable at {@code ahora} for any of the candidate token HMACs (current and previous K-SES versions):
     * not revoked, not expired and active within 30 minutes. A successful lookup records activity.
     */
    Optional<SesionGuardada> sesionVigente(List<byte[]> tokenHmacCandidatos, OffsetDateTime ahora);

    /** Revokes the open session, if any, and returns the student it belonged to (for the CIERRE audit). */
    Optional<UUID> revocarSesion(List<byte[]> tokenHmacCandidatos, OffsetDateTime cuando);

    /** D-B2.4 audit row: no code, HMAC, token or email; {@code idEstudiante} is null when unknown. */
    void auditar(String evento, UUID idEstudiante, String resultado, String origen, OffsetDateTime cuando);

    /** D-B2.4 retention. */
    Purga purgar(OffsetDateTime ahora, int diasAuditoria);

    record VinculoEstudiante(UUID userId, UUID studentId, String correo, boolean cuentaActiva) {
    }

    record CredencialNueva(UUID idCredencial, UUID userId, UUID studentId, UUID challengeId, byte[] codigoHmac,
            String kidCodigo, OffsetDateTime creada, OffsetDateTime expira) {
    }

    record CredencialGuardada(UUID challengeId, UUID userId, UUID studentId, byte[] codigoHmac, String kidCodigo,
            OffsetDateTime expira, boolean consumida, boolean revocada, int intentosFallidos) {
    }

    record SesionNueva(UUID idSesion, UUID userId, UUID studentId, byte[] tokenHmac, String kidSesion,
            OffsetDateTime creada, OffsetDateTime expira) {
    }

    record SesionGuardada(UUID userId, UUID studentId) {
    }

    record Purga(int credenciales, int sesiones, int auditoria) {
    }
}
