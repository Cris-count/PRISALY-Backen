package com.prisaly.m1.application.support;

import com.prisaly.m1.application.port.CuentaEstudiantePort;
import java.time.OffsetDateTime;
import java.util.Arrays;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** Doble de pruebas. No sustituye el adaptador JDBC. */
public final class CuentaEstudianteMemoria implements CuentaEstudiantePort {

    private final Map<UUID, VinculoEstudiante> vinculos = new ConcurrentHashMap<>();
    private final Map<String, CredencialEstudiante> correos = new ConcurrentHashMap<>();
    private final Map<UUID, ActivacionGuardada> activaciones = new ConcurrentHashMap<>();
    private final Map<String, SesionGuardada> sesiones = new ConcurrentHashMap<>();
    private final Map<String, OffsetDateTime> expiracionSesion = new ConcurrentHashMap<>();
    private final Map<String, OffsetDateTime> revocadas = new ConcurrentHashMap<>();
    public boolean fallarLectura;

    public void vincular(UUID userId, UUID studentId, String correo, boolean activa, String passwordHash) {
        vinculos.put(userId, new VinculoEstudiante(userId, studentId, activa));
        correos.put(correo, new CredencialEstudiante(userId, passwordHash, activa));
    }

    @Override
    public Optional<VinculoEstudiante> vinculo(UUID idUsuario) {
        exigir();
        return Optional.ofNullable(vinculos.get(idUsuario));
    }

    @Override
    public Optional<CredencialEstudiante> porCorreo(String correoNormalizado) {
        exigir();
        return Optional.ofNullable(correos.get(correoNormalizado));
    }

    @Override
    public void revocarActivacionesAbiertas(UUID idUsuario, OffsetDateTime cuando) {
        activaciones.replaceAll((id, actual) -> actual.userId().equals(idUsuario) && !actual.consumida() && !actual.revocada()
                ? revocada(actual)
                : actual);
    }

    @Override
    public void guardarActivacion(ActivacionNueva activacion) {
        activaciones.put(activacion.challengeId(), new ActivacionGuardada(
                activacion.challengeId(),
                activacion.userId(),
                activacion.studentId(),
                activacion.codigoHmac(),
                activacion.expira(),
                false,
                false,
                0));
    }

    @Override
    public Optional<ActivacionGuardada> buscarActivacion(UUID challengeId) {
        return Optional.ofNullable(activaciones.get(challengeId));
    }

    @Override
    public void registrarIntentoFallido(UUID challengeId) {
        activaciones.computeIfPresent(challengeId, (id, actual) -> new ActivacionGuardada(
                actual.challengeId(),
                actual.userId(),
                actual.studentId(),
                actual.codigoHmac(),
                actual.expira(),
                actual.consumida(),
                actual.revocada(),
                Math.min(5, actual.intentosFallidos() + 1)));
    }

    @Override
    public boolean consumirYActivar(UUID challengeId, String passwordHash, OffsetDateTime cuando) {
        ActivacionGuardada actual = activaciones.get(challengeId);
        if (actual == null || actual.consumida() || actual.revocada() || !actual.expira().isAfter(cuando)
                || actual.intentosFallidos() >= 5) {
            return false;
        }
        VinculoEstudiante vinculo = vinculos.get(actual.userId());
        if (vinculo == null || vinculo.cuentaActiva()) {
            return false;
        }
        activaciones.put(challengeId, new ActivacionGuardada(
                actual.challengeId(), actual.userId(), actual.studentId(), actual.codigoHmac(),
                actual.expira(), true, false, actual.intentosFallidos()));
        vinculos.put(actual.userId(), new VinculoEstudiante(actual.userId(), actual.studentId(), true));
        correos.replaceAll((correo, credencial) -> credencial.userId().equals(actual.userId())
                ? new CredencialEstudiante(actual.userId(), passwordHash, true)
                : credencial);
        return true;
    }

    @Override
    public void abrirSesion(SesionNueva sesion) {
        String clave = clave(sesion.tokenHmac());
        sesiones.put(clave, new SesionGuardada(sesion.userId(), sesion.studentId()));
        expiracionSesion.put(clave, sesion.expira());
    }

    @Override
    public Optional<SesionGuardada> sesionVigente(byte[] tokenHmac, OffsetDateTime ahora) {
        exigir();
        String clave = clave(tokenHmac);
        if (revocadas.containsKey(clave)) {
            return Optional.empty();
        }
        OffsetDateTime expira = expiracionSesion.get(clave);
        if (expira != null && !expira.isAfter(ahora)) {
            return Optional.empty();
        }
        return Optional.ofNullable(sesiones.get(clave));
    }

    @Override
    public void revocarSesion(byte[] tokenHmac, OffsetDateTime cuando) {
        revocadas.put(clave(tokenHmac), cuando);
    }

    public byte[] hmacGuardado(UUID challengeId) {
        return activaciones.get(challengeId).codigoHmac();
    }

    private void exigir() {
        if (fallarLectura) {
            throw new IllegalStateException("fallo tecnico jdbc-password-ficticia");
        }
    }

    private static ActivacionGuardada revocada(ActivacionGuardada actual) {
        return new ActivacionGuardada(
                actual.challengeId(), actual.userId(), actual.studentId(), actual.codigoHmac(),
                actual.expira(), actual.consumida(), true, actual.intentosFallidos());
    }

    private static String clave(byte[] tokenHmac) {
        return Arrays.toString(tokenHmac);
    }
}
