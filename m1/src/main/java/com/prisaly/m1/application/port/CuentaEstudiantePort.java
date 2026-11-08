package com.prisaly.m1.application.port;

import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;

/**
 * Persistencia de la cuenta estudiantil ya vinculada en BD-005.2.
 * No acepta un studentId enviado por el cliente como prueba de identidad.
 */
public interface CuentaEstudiantePort {

    Optional<VinculoEstudiante> vinculo(UUID idUsuario);

    Optional<CredencialEstudiante> porCorreo(String correoNormalizado);

    void revocarActivacionesAbiertas(UUID idUsuario, OffsetDateTime cuando);

    void guardarActivacion(ActivacionNueva activacion);

    Optional<ActivacionGuardada> buscarActivacion(UUID challengeId);

    void registrarIntentoFallido(UUID challengeId);

    boolean consumirYActivar(UUID challengeId, String passwordHash, OffsetDateTime cuando);

    void abrirSesion(SesionNueva sesion);

    Optional<SesionGuardada> sesionVigente(byte[] tokenHmac, OffsetDateTime ahora);

    void revocarSesion(byte[] tokenHmac, OffsetDateTime cuando);

    record VinculoEstudiante(UUID userId, UUID studentId, boolean cuentaActiva) {
    }

    record CredencialEstudiante(UUID userId, String passwordHash, boolean cuentaActiva) {
    }

    record ActivacionNueva(
            UUID idActivacion,
            UUID userId,
            UUID studentId,
            UUID challengeId,
            byte[] codigoHmac,
            OffsetDateTime creada,
            OffsetDateTime expira) {
    }

    record ActivacionGuardada(
            UUID challengeId,
            UUID userId,
            UUID studentId,
            byte[] codigoHmac,
            OffsetDateTime expira,
            boolean consumida,
            boolean revocada,
            int intentosFallidos) {
    }

    record SesionNueva(
            UUID idSesion,
            UUID userId,
            UUID studentId,
            byte[] tokenHmac,
            OffsetDateTime creada,
            OffsetDateTime expira) {
    }

    record SesionGuardada(UUID userId, UUID studentId) {
    }
}
